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

    // ─────────────────────────────────────────────────────────────
    // Consulta principal
    // ─────────────────────────────────────────────────────────────

    /**
     * Retorna todas las órdenes P2P activas de todas las cuentas Binance,
     * enriquecidas con la pre-asignación si existe.
     */
    public List<ActiveP2POrderDto> getAllActiveOrders() {
        List<AccountBinance> accounts = accountBinanceRepository.findByTipoAndActivaTrue("BINANCE");

        // Antes se consultaba Binance cuenta por cuenta EN SECUENCIA (se sumaban todas las esperas).
        // Ahora en PARALELO: el tiempo total pasa a ser ~el de la cuenta más lenta, no la suma.
        return accounts.parallelStream()
                .filter(a -> a.getApiKey() != null && a.getApiSecret() != null)
                .flatMap(a -> {
                    try {
                        return getActiveOrdersForAccount(a.getName()).stream();
                    } catch (Exception e) {
                        log.warn("[ActiveOrders] Error en cuenta {}: {}", a.getName(), e.getMessage());
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(java.util.stream.Collectors.toList());
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
            log.warn("[ActiveOrders] Binance error {}: {}", accountName, root.get("error").asText());
            return List.of();
        }

        List<ActiveP2POrderDto> orders = new ArrayList<>();
        JsonNode data = root.path("data");

        if (data.isArray()) {
            int totalTraidas = data.size();
            for (JsonNode obj : data) {
                String status    = obj.path("orderStatus").asText("");
                String tradeType = obj.path("tradeType").asText("");
                String asset     = obj.path("asset").asText("");
                String orderNo   = obj.path("orderNumber").asText("");

                // ── DIAGNÓSTICO temporal: registra por qué se descarta cada orden ──
                if (!ACTIVE_STATUSES.contains(status.toUpperCase())) {
                    log.info("[ActiveOrders][{}] Omitida {} → estado='{}' (tradeType={}, asset={})",
                            accountName, orderNo, status, tradeType, asset);
                    continue;
                }
                if (!"SELL".equalsIgnoreCase(tradeType)) {
                    log.info("[ActiveOrders][{}] Omitida {} → tradeType='{}' (estado={}, asset={})",
                            accountName, orderNo, tradeType, status, asset);
                    continue;
                }
                if (!"USDT".equalsIgnoreCase(asset)) {
                    log.info("[ActiveOrders][{}] Omitida {} → asset='{}' (estado={}, tradeType={})",
                            accountName, orderNo, asset, status, tradeType);
                    continue;
                }

                orders.add(buildDto(obj, accountName));
            }
            log.info("[ActiveOrders][{}] Binance devolvió {} orden(es) en la ventana de 4h; {} activa(s) tras el filtro.",
                    accountName, totalTraidas, orders.size());
        }

        return orders;
    }

    // ─────────────────────────────────────────────────────────────
    // Pre-asignación
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void upsertPreAsignacion(String orderNumber, Integer copId, String accountBinance) {
        AccountCop cop = accountCopRepository.findById(copId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta COP no encontrada: " + copId));

        P2PPreAsignacion pre = preAsignacionRepository.findByOrderNumber(orderNumber)
                .orElse(new P2PPreAsignacion());

        pre.setOrderNumber(orderNumber);
        pre.setCuentaCop(cop);
        pre.setAccountBinance(accountBinance);
        pre.setUpdatedAt(LocalDateTime.now(ZONE));
        if (pre.getCreatedAt() == null) pre.setCreatedAt(LocalDateTime.now(ZONE));

        preAsignacionRepository.save(pre);
        log.info("[PreAsign] {} → cuenta COP {} ({})", orderNumber, cop.getName(), copId);
    }

    @Transactional
    public void deletePreAsignacion(String orderNumber) {
        preAsignacionRepository.deleteByOrderNumber(orderNumber);
        log.info("[PreAsign] Removida pre-asignación de {}", orderNumber);
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
                return List.of();
            }
            pollSkipCount = 0; // llegamos al tick real, hacemos la llamada
        }

        List<ActiveP2POrderDto> allActive = getAllActiveOrders();
        List<ActiveP2POrderDto> changed   = new ArrayList<>();

        Set<String> currentOrderNumbers = new HashSet<>();

        for (ActiveP2POrderDto dto : allActive) {
            currentOrderNumbers.add(dto.getOrderNumber());
            String prev = lastKnownStatus.get(dto.getOrderNumber());
            if (!dto.getStatus().equals(prev)) {
                changed.add(dto);
                lastKnownStatus.put(dto.getOrderNumber(), dto.getStatus());
            }
        }

        // Órdenes que estaban activas y ya no aparecen → se completaron o cancelaron.
        Set<String> desaparecidas = new HashSet<>(lastKnownStatus.keySet());
        desaparecidas.removeAll(currentOrderNumbers);
        this.completadasUltimoPoll = !desaparecidas.isEmpty();

        // Limpiar órdenes que ya no están activas del cache
        lastKnownStatus.keySet().retainAll(currentOrderNumbers);

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
                copId, copNombre, estadoManual
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
