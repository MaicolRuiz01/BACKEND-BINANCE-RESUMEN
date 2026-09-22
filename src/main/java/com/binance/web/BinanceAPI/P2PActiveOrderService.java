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

    /**
     * Una orden está "en curso" mientras NO termine, y solo termina de dos formas: completada o
     * cancelada. Cualquier otro estado cuenta como activa.
     *
     * Antes había una lista blanca (TRADING, BUYER_PAYED, PENDING, IN_APPEAL) y eso dejaba fuera
     * DISTRIBUTING, que es el estado justo DESPUÉS de que el operador libera, mientras Binance
     * entrega el cripto. Esa orden desaparecía de la pantalla (no estaba en la lista blanca) y
     * tampoco se importaba (la importación solo acepta COMPLETED), así que su pre-asignación se
     * quedaba sumando en el saldo amarillo sin ninguna orden a la vista y sin pasar nunca al verde.
     * Justo lo que reportaba el cliente al minuto de liberar.
     *
     * Con la regla al revés (todo lo que no sea final está en curso), un estado nuevo o no
     * documentado de Binance se muestra en pantalla en vez de volverse invisible.
     */
    private static boolean esEstadoFinal(String status) {
        if (status == null) return false;
        String s = status.toUpperCase();
        // DISTRIBUTING cuenta como terminada igual que COMPLETED: al liberar, la venta ya se
        // registra (ver isValidSell en P2PSyncService), su plata pasa al saldo real y su
        // pre-asignación se borra. Si siguiera figurando como "en curso", el operador vería una
        // orden sin cuenta asignada cuyo dinero ya está en el verde.
        return s.equals("COMPLETED") || s.equals("DISTRIBUTING") || s.startsWith("CANCEL");
    }

    /** Estados ya vistos que no teníamos contemplados — se avisa una vez para poder etiquetarlos. */
    private final Set<String> estadosDesconocidosAvisados = ConcurrentHashMap.newKeySet();

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

    /** Slow-poll: tras el primer ciclo, si no hay NINGUNA orden activa, no se consulta a Binance
     *  en cada tick sino una vez por minuto. Va por tiempo y no por número de ticks para que
     *  siga valiendo lo mismo si se cambia el intervalo del poll (p2p.active-poll-ms). */
    private volatile boolean firstPollDone = false;
    private volatile long ultimoPollRealMs = 0;
    private static final long PAUSA_SIN_ORDENES_MS = 60_000L;

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

    /**
     * SEGUIMIENTO de órdenes que salieron del listado de las últimas 4 h sin resolverse.
     *
     * La consulta general solo pide las últimas 4 h (ver getActiveOrdersForAccount): pedirle a
     * Binance ventanas más largas cada 15 s, por cuenta, sería insostenible. Pero una orden APELADA
     * (o trabada en "pago recibido") puede durar días: pasadas las 4 h se caía del listado, y
     * entonces desaparecía de la pantalla del operador AUNQUE SIGUIERA VIVA, mientras su
     * pre-asignación seguía sumando en el saldo amarillo. De ahí venían cuentas con amarillo y
     * ninguna orden a la vista, sin que hubiera ninguna venta cancelada de por medio.
     *
     * Ahora cada orden que se cae del listado queda en seguimiento: se le pregunta a Binance por
     * ella sola (ventana corta alrededor de su fecha) cada minuto. Si sigue viva, se vuelve a
     * meter en la lista — el operador la ve y su monto cuenta con razón. Si se completó o se
     * canceló, sale del seguimiento y la maneja el sync.
     */
    private record Seguimiento(String accountBinance, long createTimeMs, long desaparecioEnMs,
                               long ultimaRevisionMs) {}

    private final Map<String, Seguimiento> enSeguimiento = new ConcurrentHashMap<>();
    /** Órdenes confirmadas vivas fuera de la ventana de 4 h — se mezclan en el listado activo. */
    private final Map<String, ActiveP2POrderDto> vivasFueraDeVentana = new ConcurrentHashMap<>();
    /**
     * Cada cuánto se le vuelve a preguntar a Binance por una orden en seguimiento.
     *
     * Los primeros minutos se pregunta en CADA vuelta del poll (5 s) porque ese es el caso del
     * operador que acaba de liberar: Binance tarda un rato en reflejar la orden en su historial, y
     * hay que estar encima para registrarla apenas aparezca. Pasado ese ratito, la orden ya no es
     * una venta recién liberada sino algo trabado (apelada, por ejemplo), y basta revisarla
     * cada minuto.
     */
    private static final long SEGUIMIENTO_INTENSIVO_MS = 2 * 60_000L;
    private static final long REVISION_LENTA_MS = 60_000L;
    /** Si Binance no la devuelve en todo este tiempo, se deja de seguir (y de contar en amarillo). */
    private static final long MAX_SIN_RESOLVER_MS = 15 * 60_000L;

    /**
     * Última vez (ms) que cada orden se vio ACTIVA en Binance, por cualquier consulta (poll de 15 s,
     * pantalla, asignación automática…). Sirve para que el saldo amarillo cuente SOLO pre-asignaciones
     * de órdenes que de verdad están en curso, y no filas huérfanas (órdenes canceladas o vencidas
     * que nunca se importaron) que inflaban el amarillo de una cuenta sin ninguna venta a la vista.
     */
    private final Map<String, Long> vistaActivaEn = new ConcurrentHashMap<>();
    /** Una orden cuenta como "en curso" si se vio activa hace menos de esto (o si sigue en seguimiento). */
    private static final long VIGENCIA_VISTA_MS = 2 * 60_000L;

    /** Desde cuándo viene fallando cada cuenta Binance (se limpia al primer éxito). */
    private final Map<String, Long> erroresCuentaDesde = new ConcurrentHashMap<>();
    /** Cuánto se les da a las órdenes de una cuenta que falla antes de dejar de darlas por activas. */
    private static final long MARGEN_CUENTA_CON_ERROR_MS = 10 * 60_000L;

    /**
     * Órdenes cuya pre-asignación debe sumar en el amarillo: activas ahora mismo, o recién
     * completadas esperando importarse (su monto pasa al saldo real al importarse).
     */
    public Set<String> ordenesQueCuentanEnCurso() {
        long ahora = Instant.now().toEpochMilli();
        vistaActivaEn.values().removeIf(t -> ahora - t > MAX_SIN_RESOLVER_MS);

        Set<String> out = new HashSet<>();
        vistaActivaEn.forEach((on, t) -> { if (ahora - t <= VIGENCIA_VISTA_MS) out.add(on); });
        // Órdenes de cuentas que fallaron: siguen en el cache del poll aunque no se hayan podido leer.
        out.addAll(lastKnownStatus.keySet());
        // Órdenes que salieron del listado y todavía NO se resolvieron: su plata no está en el
        // saldo real todavía, así que sigue siendo amarillo. Se dejan de contar solo cuando se
        // resuelven (se registró la venta o se canceló) o cuando Binance no las devuelve en
        // MAX_SIN_RESOLVER_MS. Antes se dejaban de contar a los pocos minutos pasara lo que
        // pasara, y por eso el monto de una venta recién liberada desaparecía de la pantalla sin
        // haber llegado al verde: ni en un lado ni en el otro.
        out.addAll(enSeguimiento.keySet());
        return out;
    }

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
        List<ActiveP2POrderDto> ordenes = new ArrayList<>(accounts.parallelStream()
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
                .collect(java.util.stream.Collectors.toList()));

        // Órdenes vivas que quedaron fuera de la ventana de 4 h (apeladas, trabadas): se mezclan
        // para que el operador las siga viendo y su monto siga contando con razón.
        Set<String> presentes = new HashSet<>();
        for (ActiveP2POrderDto o : ordenes) presentes.add(o.getOrderNumber());
        for (ActiveP2POrderDto vieja : vivasFueraDeVentana.values()) {
            if (presentes.contains(vieja.getOrderNumber())) continue;
            if (conError.contains(vieja.getAccountBinance())) continue;
            // Copia: el DTO guardado lo comparten todas las consultas (pantallas + poll), así que
            // no se muta el original — cada respuesta se arma con su propia copia al día.
            ActiveP2POrderDto copia = new ActiveP2POrderDto(
                    vieja.getOrderNumber(), vieja.getStatus(), vieja.getStatusLabel(),
                    vieja.getAccountBinance(), vieja.getDollarsUs(), vieja.getPesosCop(),
                    vieja.getTasa(), vieja.getCreateTime(), null, null, "PENDIENTE",
                    vieja.getCounterPartNickName());
            aplicarPreAsignacion(copia);   // su cuenta COP pudo cambiar desde la última revisión
            ordenes.add(copia);
        }

        ordenes.sort(Comparator
                .comparing((ActiveP2POrderDto o) -> o.getCreateTime() == null ? "" : o.getCreateTime())
                .thenComparing(o -> o.getOrderNumber() == null ? "" : o.getOrderNumber())
                .reversed());

        long ahora = Instant.now().toEpochMilli();
        for (ActiveP2POrderDto o : ordenes) {
            vistaActivaEn.put(o.getOrderNumber(), ahora);
        }
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
                if (status.isBlank() || esEstadoFinal(status)) continue;
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
        if (firstPollDone && lastKnownStatus.isEmpty() && enSeguimiento.isEmpty()
                && Instant.now().toEpochMilli() - ultimoPollRealMs < PAUSA_SIN_ORDENES_MS) {
            log.debug("[ActiveOrders] Sin órdenes activas — se consulta una vez por minuto");
            this.desaparecidasUltimoPoll = List.of();
            this.ordenesUltimoPoll = List.of();
            this.completadasUltimoPoll = false;
            return List.of();
        }
        ultimoPollRealMs = Instant.now().toEpochMilli();

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

        // Las órdenes de una cuenta que FALLÓ no desaparecieron: no se pudieron leer, así que se
        // conservan mientras la cuenta se recupera. PERO SOLO UN RATO: si una cuenta queda fallando
        // (llave revocada, Binance bloqueando la IP…), conservarlas para siempre dejaba su monto
        // sumando eternamente en el saldo amarillo, sin ninguna orden a la vista y sin forma de
        // que se resolviera. Pasado el margen, se sueltan y cada una entra al seguimiento uno a uno.
        long ahoraErr = Instant.now().toEpochMilli();
        for (String cuenta : cuentasConError) {
            erroresCuentaDesde.putIfAbsent(cuenta, ahoraErr);
        }
        erroresCuentaDesde.keySet().removeIf(cuenta -> !cuentasConError.contains(cuenta));

        for (String on : lastKnownStatus.keySet()) {
            OrdenDesaparecida info = infoConocida.get(on);
            if (info == null || !cuentasConError.contains(info.accountBinance())) continue;
            long desde = erroresCuentaDesde.getOrDefault(info.accountBinance(), ahoraErr);
            if (ahoraErr - desde <= MARGEN_CUENTA_CON_ERROR_MS) {
                currentOrderNumbers.add(on);
            } else {
                log.warn("[ActiveOrders] La cuenta {} lleva {} min fallando: la orden {} deja de darse por activa "
                        + "y pasa a revisión individual.", info.accountBinance(),
                        (ahoraErr - desde) / 60_000, on);
            }
        }

        // Órdenes que estaban activas y ya no aparecen → se completaron o cancelaron.
        Set<String> desaparecidas = new HashSet<>(lastKnownStatus.keySet());
        desaparecidas.removeAll(currentOrderNumbers);
        this.completadasUltimoPoll = !desaparecidas.isEmpty();

        List<OrdenDesaparecida> detalle = new ArrayList<>();
        long ahoraMs = Instant.now().toEpochMilli();
        for (String on : desaparecidas) {
            OrdenDesaparecida info = infoConocida.get(on);
            if (info != null) detalle.add(info);
            vistaActivaEn.remove(on);
            if (info != null && !enSeguimiento.containsKey(on)) {
                // Se le va a preguntar a Binance por ella sola: puede haberse completado, cancelado
                // o simplemente haber salido de la ventana de 4 h estando todavía viva.
                enSeguimiento.put(on, new Seguimiento(info.accountBinance(), info.createTimeMs(), ahoraMs, 0L));
            }
        }
        this.desaparecidasUltimoPoll = detalle;
        this.ordenesUltimoPoll = allActive;

        // Limpiar órdenes que ya no están activas del cache
        lastKnownStatus.keySet().retainAll(currentOrderNumbers);
        infoConocida.keySet().retainAll(currentOrderNumbers);

        completarMontosFaltantes(allActive);

        firstPollDone = true;
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

    /**
     * Revisa una por una las órdenes en seguimiento (las que se cayeron del listado de 4 h).
     * Lo llama el poll cada 15 s, pero a cada orden se le pregunta como mucho una vez por minuto.
     */
    public void revisarSeguimiento() {
        if (enSeguimiento.isEmpty()) return;
        long ahora = Instant.now().toEpochMilli();

        // Se agrupan por cuenta para hacer UNA consulta por cuenta, no una por orden.
        Map<String, List<String>> porCuenta = new HashMap<>();
        long inicioVentana = ahora;
        for (Map.Entry<String, Seguimiento> e : enSeguimiento.entrySet()) {
            Seguimiento s = e.getValue();
            boolean recienDesaparecida = ahora - s.desaparecioEnMs() <= SEGUIMIENTO_INTENSIVO_MS;
            long cada = recienDesaparecida ? 0L : REVISION_LENTA_MS;
            if (ahora - s.ultimaRevisionMs() < cada) continue;
            porCuenta.computeIfAbsent(s.accountBinance(), k -> new ArrayList<>()).add(e.getKey());
            inicioVentana = Math.min(inicioVentana, s.createTimeMs() - 2 * 60_000L);
        }
        if (porCuenta.isEmpty()) return;

        for (Map.Entry<String, List<String>> e : porCuenta.entrySet()) {
            String cuenta = e.getKey();
            try {
                String json = binanceService.getP2POrdersInRange(cuenta, inicioVentana, ahora, "SELL");
                JsonNode root = mapper.readTree(json);
                if (root.has("error")) {
                    log.warn("[Seguimiento] Binance error en {}: {}", cuenta, root.get("error").asText());
                    continue; // se reintenta en la próxima vuelta; no se descarta nada
                }

                Map<String, JsonNode> porOrden = new HashMap<>();
                JsonNode data = root.path("data");
                if (data.isArray()) {
                    for (JsonNode obj : data) porOrden.put(obj.path("orderNumber").asText(), obj);
                }

                for (String orderNumber : e.getValue()) {
                    Seguimiento s = enSeguimiento.get(orderNumber);
                    if (s == null) continue;
                    JsonNode obj = porOrden.get(orderNumber);

                    if (obj == null) {
                        // Binance todavía no la refleja en su historial: al liberar, tarda un rato.
                        // Se sigue preguntando (y su monto sigue en amarillo) hasta el tope.
                        if (ahora - s.desaparecioEnMs() >= MAX_SIN_RESOLVER_MS) {
                            enSeguimiento.remove(orderNumber);
                            vivasFueraDeVentana.remove(orderNumber);
                            log.warn("[Seguimiento] Binance no devuelve la orden {} ({}) desde hace {} min; "
                                    + "deja de contarse como en curso.", orderNumber, cuenta,
                                    (ahora - s.desaparecioEnMs()) / 60_000);
                        } else {
                            enSeguimiento.put(orderNumber, new Seguimiento(
                                    s.accountBinance(), s.createTimeMs(), s.desaparecioEnMs(), ahora));
                        }
                        continue;
                    }

                    String status = obj.path("orderStatus").asText("").toUpperCase();
                    if (!esEstadoFinal(status)) {
                        // Sigue viva (típicamente apelada): vuelve al listado que ve el operador.
                        vivasFueraDeVentana.put(orderNumber, buildDto(obj, cuenta));
                        enSeguimiento.put(orderNumber, new Seguimiento(
                                s.accountBinance(), s.createTimeMs(), s.desaparecioEnMs(), ahora));
                        log.info("[Seguimiento] La orden {} ({}) sigue viva con estado {} fuera de la ventana de 4 h.",
                                orderNumber, cuenta, status);
                    } else {
                        // Terminada (liberada, completada o cancelada). Se pide su importación
                        // puntual YA: si hay venta, su plata pasa al verde en el acto; si se
                        // canceló, se borra su pre-asignación.
                        vivasFueraDeVentana.remove(orderNumber);
                        try {
                            syncService.importarOrdenesRecientes(cuenta, s.createTimeMs() - 2 * 60_000L,
                                    Set.of(orderNumber));
                        } catch (Exception ex) {
                            log.warn("[Seguimiento] No se pudo importar la orden {} ({}): {}",
                                    orderNumber, cuenta, ex.getMessage());
                        }
                        // Solo se deja de seguir cuando de verdad se resolvió: o quedó la venta
                        // registrada, o se canceló. Si la importación falló, se sigue intentando y
                        // su monto sigue en amarillo, que es donde está la plata mientras tanto.
                        if (status.startsWith("CANCEL") || syncService.ventaRegistrada(orderNumber)) {
                            enSeguimiento.remove(orderNumber);
                        } else {
                            enSeguimiento.put(orderNumber, new Seguimiento(
                                    s.accountBinance(), s.createTimeMs(), s.desaparecioEnMs(), ahora));
                            log.warn("[Seguimiento] La orden {} ({}) está {} pero su venta no quedó "
                                    + "registrada; se reintenta.", orderNumber, cuenta, status);
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[Seguimiento] No se pudo revisar las órdenes de {}: {}", cuenta, ex.getMessage());
            }
        }
    }

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

        ActiveP2POrderDto dto = new ActiveP2POrderDto(
                orderNumber, status, statusLabel(status),
                accountName, dollarsUs, pesosCop, tasa, createTime,
                null, null, "PENDIENTE", counterPartNickName
        );
        aplicarPreAsignacion(dto);
        return dto;
    }

    /** Copia al DTO la cuenta COP pre-asignada (si existe) — dato que vive en la BD, no en Binance. */
    private void aplicarPreAsignacion(ActiveP2POrderDto dto) {
        Optional<P2PPreAsignacion> pre = preAsignacionRepository.findByOrderNumber(dto.getOrderNumber());
        if (pre.isPresent()) {
            dto.setPreAsignadoCopId(pre.get().getCuentaCop().getId());
            dto.setPreAsignadoCopNombre(pre.get().getCuentaCop().getName());
            dto.setEstadoManual(pre.get().getEstadoManual() != null ? pre.get().getEstadoManual() : "PENDIENTE");
        } else {
            dto.setPreAsignadoCopId(null);
            dto.setPreAsignadoCopNombre(null);
        }
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
            case "TRADING"      -> "En curso";
            case "BUYER_PAYED"  -> "Pago recibido";
            case "PENDING"      -> "Pendiente";
            case "IN_APPEAL"    -> "Apelada";
            // Ya liberaste y Binance está entregando el cripto: la venta se completa en seguida.
            case "DISTRIBUTING" -> "Liberando";
            default -> {
                // Estado no contemplado: se muestra tal cual (la orden SÍ se ve, que es lo
                // importante) y queda avisado una vez para poder ponerle etiqueta en español.
                if (estadosDesconocidosAvisados.add(status)) {
                    log.warn("[ActiveOrders] Estado de Binance no contemplado: '{}'. La orden se "
                            + "muestra igual como en curso.", status);
                }
                yield status;
            }
        };
    }
}
