package com.binance.web.BinanceAPI;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.P2PPreAsignacion;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.P2PPreAsignacionRepository;
import com.binance.web.dto.ActiveP2POrderDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consulta órdenes P2P abiertas desde Binance y gestiona pre-asignaciones.
 *
 * Las órdenes activas NO se guardan en BD — se traen en tiempo real.
 * Solo se persiste la pre-asignación (cuenta COP elegida por el operador).
 */
@Slf4j
@Service
public class P2PActiveOrderService {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Estados de Binance que consideramos "activos" (no terminales).
     *  IN_APPEAL = venta en disputa/apelada: sigue "viva" (aún no se libera/cancela el cripto),
     *  el cliente quiere verla en la vista P2P para gestionarla, así que la tratamos como activa. */
    private static final Set<String> ACTIVE_STATUSES = Set.of("TRADING", "BUYER_PAYED", "PENDING", "IN_APPEAL");

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired private BinanceService binanceService;
    @Autowired private AccountBinanceRepository accountBinanceRepository;
    @Autowired private P2PPreAsignacionRepository preAsignacionRepository;
    @Autowired private AccountCopRepository accountCopRepository;
    /** @Lazy: evita una referencia circular al arrancar (el sync no depende de este servicio,
     *  pero la cadena de servicios que usa sí podría). */
    @Autowired @org.springframework.context.annotation.Lazy private P2PSyncService syncService;

    /**
     * Cache en memoria del último estado conocido por orderNumber.
     * Usado por el scheduler para detectar cambios sin ir a BD.
     * key = orderNumber, value = status
     */
    private final Map<String, String> lastKnownStatus = new ConcurrentHashMap<>();

    /** Slow-poll: tras el primer ciclo, si no hay órdenes activas, solo llamamos
     *  Binance cada MAX_SKIP+1 ticks (efectivamente cada ~60 s con tick=15 s). */
    private volatile boolean firstPollDone = false;
    private int pollSkipCount = 0;
    private static final int MAX_SKIP = 3; // 3 skips → 1 call real cada 4 ticks

    /** true si en el último poll alguna orden salió del listado activo (se completó/canceló).
     *  Lo usa el scheduler para importar de inmediato y sumar el saldo sin esperar 3 min. */
    private volatile boolean completadasUltimoPoll = false;
    public boolean huboCompletadasEnUltimoPoll() { return completadasUltimoPoll; }

    /** Cuenta Binance y hora de creación de cada orden activa conocida (key = orderNumber).
     *  Cuando una orden desaparece, con esto se sabe qué cuenta y qué ventana importar. */
    private final Map<String, OrdenDesaparecida> infoConocida = new ConcurrentHashMap<>();

    /** Órdenes que salieron del listado activo en el último poll (se completaron o cancelaron). */
    private volatile List<OrdenDesaparecida> desaparecidasUltimoPoll = List.of();
    public List<OrdenDesaparecida> getDesaparecidasUltimoPoll() { return desaparecidasUltimoPoll; }

    /** Órdenes activas leídas en el último poll (vacío si ese tick no consultó Binance).
     *  La asignación automática las reutiliza en vez de volver a pedírselas a Binance. */
    private volatile List<ActiveP2POrderDto> ordenesUltimoPoll = List.of();
    public List<ActiveP2POrderDto> getOrdenesUltimoPoll() { return ordenesUltimoPoll; }

    public record OrdenDesaparecida(String orderNumber, String accountBinance, long createTimeMs) {}

    /** Resultado de consultar todas las cuentas: las órdenes y qué cuentas fallaron. */
    public record ConsultaActivas(List<ActiveP2POrderDto> ordenes, Set<String> cuentasConError) {}

    // ─────────────────────────────────────────────────────────────
    // Consulta principal
    // ─────────────────────────────────────────────────────────────

    /**
     * Retorna todas las órdenes P2P activas de todas las cuentas Binance,
     * enriquecidas con la pre-asignación si existe.
     */
    public List<ActiveP2POrderDto> getAllActiveOrders() {
        return consultarActivas().ordenes();
    }

    /**
     * Igual que {@link #getAllActiveOrders()} pero además informa qué cuentas fallaron.
     * Importa para el poll: si una cuenta falla, sus órdenes NO desaparecieron — simplemente
     * no se pudieron leer. Tratarlas como completadas disparaba importaciones y avisos falsos.
     */
    public ConsultaActivas consultarActivas() {
        List<AccountBinance> accounts = accountBinanceRepository.findByTipoAndActivaTrue("BINANCE");
        Set<String> conError = ConcurrentHashMap.newKeySet();

        // Antes se consultaba Binance cuenta por cuenta EN SECUENCIA (se sumaban todas las esperas).
        // Ahora en PARALELO: el tiempo total pasa a ser ~el de la cuenta más lenta, no la suma.
        List<ActiveP2POrderDto> ordenes = accounts.parallelStream()
                .filter(a -> a.getApiKey() != null && a.getApiSecret() != null)
                .flatMap(a -> {
                    try {
                        return getActiveOrdersForAccount(a.getName()).stream();
                    } catch (Exception e) {
                        log.warn("[ActiveOrders] Error en cuenta {}: {}", a.getName(), e.getMessage());
                        conError.add(a.getName());
                        return java.util.stream.Stream.empty();
                    }
                })
                // ORDEN GLOBAL — de la venta más reciente a la más antigua.
                //
                // Antes no se ordenaba en ninguna parte: ni acá, ni en el frontend, ni en la
                // tabla. El resultado era la concatenación de las listas de cada cuenta, así que
                // con UNA sola cuenta parecía ordenado (Binance devuelve sus órdenes seguidas)
                // pero con DOS quedaban en bloques: primero todas las de una cuenta y después
                // todas las de la otra, mezclando fechas. El operador veía la lista desordenada.
                //
                // createTime viene como "yyyy-MM-dd HH:mm:ss", que ordenado como texto da el
                // mismo resultado que ordenado como fecha, sin tener que volver a parsearlo.
                // Las órdenes sin fecha van al final (cadena vacía).
                //
                // El desempate por orderNumber no es cosmético: si dos órdenes caen en el mismo
                // segundo y el orden entre ellas quedara al azar, se intercambiarían de lugar en
                // cada refresco y la fila que el operador está mirando le saltaría sola.
                .sorted(Comparator
                        .comparing((ActiveP2POrderDto o) ->
                                o.getCreateTime() == null ? "" : o.getCreateTime())
                        .thenComparing(o -> o.getOrderNumber() == null ? "" : o.getOrderNumber())
                        .reversed())
                .collect(java.util.stream.Collectors.toList());
        return new ConsultaActivas(ordenes, conError);
    }

    /**
     * Retorna órdenes activas de una cuenta específica.
     */
    public List<ActiveP2POrderDto> getActiveOrdersForAccount(String accountName) throws Exception {
        // Ventana: últimas 4h (P2P órdenes raramente duran más; reduce llamadas Binance)
        long endMs   = Instant.now().toEpochMilli();
        long startMs = endMs - (4L * 60 * 60 * 1000);

        String json   = binanceService.getP2POrdersInRange(accountName, startMs, endMs, "SELL");
        JsonNode root = mapper.readTree(json);

        if (root.has("error")) {
            // Se lanza en vez de devolver una lista vacía: "Binance falló" y "no hay órdenes" son
            // cosas distintas, y confundirlas hacía creer que las órdenes se habían completado.
            throw new IllegalStateException("Binance error en " + accountName + ": " + root.get("error").asText());
        }

        List<ActiveP2POrderDto> orders = new ArrayList<>();
        JsonNode data = root.path("data");

        if (data.isArray()) {
            for (JsonNode obj : data) {
                String status = obj.path("orderStatus").asText("");
                if (!ACTIVE_STATUSES.contains(status.toUpperCase())) continue;
                if (!"SELL".equalsIgnoreCase(obj.path("tradeType").asText())) continue;
                if (!"USDT".equalsIgnoreCase(obj.path("asset").asText())) continue;

                orders.add(buildDto(obj, accountName));
            }
        }

        return orders;
    }

    // ─────────────────────────────────────────────────────────────
    // Pre-asignación
    // ─────────────────────────────────────────────────────────────

    /**
     * @param pesosCop monto de la orden en MILES (puede ser null: se conserva el que ya tenía,
     *                 o lo completa el poll). Se guarda para sumar el verde/amarillo desde la BD.
     */
    @Transactional
    public void upsertPreAsignacion(String orderNumber, Integer copId, String accountBinance, Double pesosCop) {
        AccountCop cop = accountCopRepository.findById(copId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta COP no encontrada: " + copId));

        P2PPreAsignacion pre = preAsignacionRepository.findByOrderNumber(orderNumber)
                .orElse(new P2PPreAsignacion());

        pre.setOrderNumber(orderNumber);
        pre.setCuentaCop(cop);
        pre.setAccountBinance(accountBinance);
        if (pesosCop != null) pre.setPesosCop(pesosCop);
        pre.setUpdatedAt(LocalDateTime.now(ZONE));
        if (pre.getCreatedAt() == null) pre.setCreatedAt(LocalDateTime.now(ZONE));

        preAsignacionRepository.saveAndFlush(pre);
        log.info("[PreAsign] {} → cuenta COP {} ({})", orderNumber, cop.getName(), copId);

        // Si la venta ya se importó (se completó justo antes de asignar), aplicarla ya.
        syncService.aplicarPreAsignacionSiYaSeImporto(orderNumber);
        AccountCopSaldoListener.notificarTrasCommit();
    }

    /** true si la orden ya tiene pre-asignación guardada (la asignación automática no la pisa). */
    public boolean tienePreAsignacion(String orderNumber) {
        return preAsignacionRepository.existsByOrderNumber(orderNumber);
    }

    @Transactional
    public void deletePreAsignacion(String orderNumber) {
        preAsignacionRepository.deleteByOrderNumber(orderNumber);
        log.info("[PreAsign] Removida pre-asignación de {}", orderNumber);
        AccountCopSaldoListener.notificarTrasCommit();
    }

    /** Completa pesos_cop en filas viejas (guardadas antes de existir la columna). */
    private void completarMontosFaltantes(List<ActiveP2POrderDto> activas) {
        try {
            List<P2PPreAsignacion> sinMonto = preAsignacionRepository.findByPesosCopIsNull();
            if (sinMonto.isEmpty()) return;
            Map<String, Double> montos = new HashMap<>();
            for (ActiveP2POrderDto o : activas) montos.put(o.getOrderNumber(), o.getPesosCop());
            for (P2PPreAsignacion pre : sinMonto) {
                Double m = montos.get(pre.getOrderNumber());
                if (m != null) {
                    pre.setPesosCop(m);
                    preAsignacionRepository.save(pre);
                }
            }
        } catch (Exception e) {
            log.warn("[PreAsign] No se pudieron completar montos faltantes: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Detección de cambios de estado (para SSE)
    // ─────────────────────────────────────────────────────────────

    /**
     * Consulta órdenes activas de todas las cuentas y devuelve las que
     * cambiaron de estado respecto al último polling.
     * También actualiza el cache interno.
     *
     * Optimización: tras el primer ciclo, si no hay órdenes activas conocidas,
     * salta hasta MAX_SKIP ticks consecutivos para reducir llamadas a Binance
     * (efectivamente pasa de poll cada 15 s → cada 60 s cuando no hay nada).
     */
    public List<ActiveP2POrderDto> detectStatusChanges() {
        // Slow-poll: omitir llamada a Binance cuando no hay órdenes activas
        if (firstPollDone && lastKnownStatus.isEmpty()) {
            pollSkipCount++;
            if (pollSkipCount < MAX_SKIP) {
                log.debug("[ActiveOrders] Sin órdenes activas — omitiendo poll {}/{}", pollSkipCount, MAX_SKIP);
                this.desaparecidasUltimoPoll = List.of();
                this.ordenesUltimoPoll = List.of();
                this.completadasUltimoPoll = false;
                return List.of();
            }
            pollSkipCount = 0; // llegamos al tick real, hacemos la llamada
        }

        ConsultaActivas consulta = consultarActivas();
        List<ActiveP2POrderDto> allActive = consulta.ordenes();
        Set<String> cuentasConError = consulta.cuentasConError();
        List<ActiveP2POrderDto> changed   = new ArrayList<>();

        Set<String> currentOrderNumbers = new HashSet<>();

        for (ActiveP2POrderDto dto : allActive) {
            currentOrderNumbers.add(dto.getOrderNumber());
            infoConocida.put(dto.getOrderNumber(), new OrdenDesaparecida(
                    dto.getOrderNumber(), dto.getAccountBinance(), parseCreateTime(dto.getCreateTime())));
            String prev = lastKnownStatus.get(dto.getOrderNumber());
            if (!dto.getStatus().equals(prev)) {
                changed.add(dto);
                lastKnownStatus.put(dto.getOrderNumber(), dto.getStatus());
            }
        }

        // Las órdenes de una cuenta que FALLÓ no desaparecieron: no se pudieron leer.
        // Se conservan tal cual hasta que la cuenta vuelva a responder.
        for (String on : lastKnownStatus.keySet()) {
            OrdenDesaparecida info = infoConocida.get(on);
            if (info != null && cuentasConError.contains(info.accountBinance())) {
                currentOrderNumbers.add(on);
            }
        }

        // Órdenes que estaban activas y ya no aparecen → se completaron o cancelaron.
        Set<String> desaparecidas = new HashSet<>(lastKnownStatus.keySet());
        desaparecidas.removeAll(currentOrderNumbers);
        this.completadasUltimoPoll = !desaparecidas.isEmpty();

        List<OrdenDesaparecida> detalle = new ArrayList<>();
        for (String on : desaparecidas) {
            OrdenDesaparecida info = infoConocida.get(on);
            if (info != null) detalle.add(info);
        }
        this.desaparecidasUltimoPoll = detalle;
        this.ordenesUltimoPoll = allActive;

        // Limpiar órdenes que ya no están activas del cache
        lastKnownStatus.keySet().retainAll(currentOrderNumbers);
        infoConocida.keySet().retainAll(currentOrderNumbers);

        completarMontosFaltantes(allActive);

        firstPollDone = true;
        // Si volvieron a aparecer órdenes, salimos del modo slow
        if (!allActive.isEmpty()) {
            pollSkipCount = 0;
        }

        return changed;
    }

    public List<ActiveP2POrderDto> getLastKnownActiveOrders() {
        return getAllActiveOrders(); // siempre fresco desde Binance
    }

    /**
     * ¿Hay ventas P2P en curso ahora mismo? Lee el cache en memoria que ya mantiene
     * detectStatusChanges() (poll cada 15s), así que NO hace llamadas extra a Binance.
     * Lo usa el vigilante de jornada para saber si el operador está recibiendo órdenes.
     */
    public boolean hayOrdenesActivas() {
        return !lastKnownStatus.isEmpty();
    }

    /** true si ya se hizo al menos un poll — para no alarmar antes de tener datos reales. */
    public boolean yaHizoPrimerPoll() {
        return firstPollDone;
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /** "yyyy-MM-dd HH:mm:ss" (hora Bogotá) → epoch ms. Si no se puede leer, 4 h atrás (la ventana activa). */
    private long parseCreateTime(String createTime) {
        try {
            if (createTime != null && !createTime.isBlank()) {
                return LocalDateTime.parse(createTime, FMT).atZone(ZONE).toInstant().toEpochMilli();
            }
        } catch (Exception ignored) { }
        return Instant.now().toEpochMilli() - (4L * 60 * 60 * 1000);
    }

    private ActiveP2POrderDto buildDto(JsonNode obj, String accountName) {
        String orderNumber   = obj.path("orderNumber").asText();
        String status        = obj.path("orderStatus").asText("").toUpperCase();
        double pesosCopRaw   = obj.path("totalPrice").asDouble(0.0);
        double pesosCop      = pesosCopRaw / 1_000.0;
        double dollarsUs     = obj.path("amount").asDouble(0.0) / 1_000.0;
        double tasa          = obj.path("unitPrice").asDouble(0.0);
        long   createTimeLong = obj.path("createTime").asLong(0);
        String createTime    = createTimeLong > 0
                ? Instant.ofEpochMilli(createTimeLong).atZone(ZONE).format(FMT)
                : "";
        String counterPartNickName = obj.path("counterPartNickName").asText("");

        // Enriquecer con pre-asignación si existe
        Integer copId   = null;
        String  copNombre = null;
        String  estadoManual = "PENDIENTE";
        Optional<P2PPreAsignacion> pre = preAsignacionRepository.findByOrderNumber(orderNumber);
        if (pre.isPresent()) {
            copId     = pre.get().getCuentaCop().getId();
            copNombre = pre.get().getCuentaCop().getName();
            if (pre.get().getEstadoManual() != null) estadoManual = pre.get().getEstadoManual();
        }

        return new ActiveP2POrderDto(
                orderNumber, status, statusLabel(status),
                accountName, dollarsUs, pesosCop, tasa, createTime,
                copId, copNombre, estadoManual, counterPartNickName
        );
    }

    /** Cambia el estado manual (PENDIENTE / RECIBIDO) de una orden pre-asignada. */
    @Transactional
    public void setEstadoManual(String orderNumber, String estado) {
        String norm = "RECIBIDO".equalsIgnoreCase(estado) ? "RECIBIDO" : "PENDIENTE";
        P2PPreAsignacion pre = preAsignacionRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La orden no está pre-asignada a ninguna cuenta: " + orderNumber));
        pre.setEstadoManual(norm);
        pre.setUpdatedAt(LocalDateTime.now(ZONE));
        preAsignacionRepository.save(pre);
        AccountCopSaldoListener.notificarTrasCommit();
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "TRADING"     -> "En curso";
            case "BUYER_PAYED" -> "Pago recibido";
            case "PENDING"     -> "Pendiente";
            case "IN_APPEAL"   -> "Apelada";
            default            -> status;
        };
    }
}
