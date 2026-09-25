package com.binance.web.BinanceAPI;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.binance.web.Entity.*;
import com.binance.web.Repository.*;
import com.binance.web.service.AccountBinanceService;
import com.binance.web.service.AccountCopService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sincronización delta con Binance P2P.
 *
 * En lugar de pedir TODAS las órdenes del día en cada llamado,
 * guarda el timestamp de la última sync por cuenta y solo pide
 * las órdenes que llegaron DESDE ese timestamp.
 *
 * Resultado: si la última sync fue hace 3 min y hubo 2 órdenes nuevas,
 * solo se procesan esas 2 — no las 100+ del día completo.
 */
@Slf4j
@Service
public class P2PSyncService {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final ObjectMapper mapper = new ObjectMapper();

    /** Evita que dos sincronizaciones corran a la vez (scheduler de 3min + trigger al completar),
     *  lo que causaba el error de "Duplicate entry" en number_order por carrera. */
    private final java.util.concurrent.atomic.AtomicBoolean syncEnCurso =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Si se pide una sync completa mientras otra corre, queda anotada y se repite al terminar. */
    private final java.util.concurrent.atomic.AtomicBoolean completaPendiente =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /** Un candado por número de orden: evita guardar dos veces la misma sin frenar a las demás. */
    private final java.util.concurrent.ConcurrentHashMap<String, Object> candadosPorOrden =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Pedido de importación puntual: cuenta, ventana a pedirle a Binance y órdenes a resolver. */
    private record Rapida(long desdeMs, java.util.Set<String> ordenes) {
        Rapida unir(Rapida otra) {
            java.util.Set<String> todas = new java.util.HashSet<>(this.ordenes);
            todas.addAll(otra.ordenes);
            return new Rapida(Math.min(this.desdeMs, otra.desdeMs), todas);
        }
    }

    /** Órdenes ya avisadas como "registrada pero cancelada" — para no repetir el log cada minuto. */
    private final java.util.Set<String> canceladasYaAvisadas =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Autowired private BinanceService binanceService;
    @Autowired private SaleP2PRepository saleP2PRepository;
    @Autowired private AccountBinanceRepository accountBinanceRepository;
    @Autowired private P2PSyncStateRepository syncStateRepository;
    @Autowired private P2PPreAsignacionRepository preAsignacionRepository;
    @Autowired private AccountCopService accountCopService;
    @Autowired private AccountBinanceService accountBinanceService;
    @Autowired private com.binance.web.service.UtilidadP2PCalculator utilidadCalculator;

    /** Horas hacia atrás que se le piden a Binance. Cubre el cruce de medianoche (ver resolveStartMs). */
    @org.springframework.beans.factory.annotation.Value("${p2p.sync.lookback-horas:36}")
    private long lookbackHoras;

    /** Referencia a sí mismo (vía proxy) para que el @Transactional de persistirVenta y
     *  actualizarEstadoSync SÍ aplique al llamarlos desde syncAccount, que no es transaccional
     *  (evita el problema de auto-invocación de Spring: una llamada interna se salta el proxy). */
    @Autowired @Lazy private P2PSyncService self;

    // ─────────────────────────────────────────────────────────────
    // Punto de entrada principal
    // ─────────────────────────────────────────────────────────────

    /**
     * Sincroniza todas las cuentas Binance registradas.
     * @return número total de ventas P2P nuevas encontradas y guardadas
     */
    public int syncAllAccounts() {
        // La sync COMPLETA sigue siendo de a una por vez (recorre 36 h de todas las cuentas y
        // tarda), pero YA NO BLOQUEA a las importaciones puntuales.
        if (!syncEnCurso.compareAndSet(false, true)) {
            completaPendiente.set(true);   // hay otra corriendo: se repite al terminar
            log.debug("[Sync] Ya hay una sync completa corriendo; se encola.");
            return 0;
        }
        int total = 0;
        try {
            do {
                completaPendiente.set(false);
                long t0 = System.currentTimeMillis();
                log.info("[Sync] Sync completa: inicio");
                total += ejecutarCompleta();
                log.info("[Sync] Sync completa: fin en {} ms ({} venta(s) nueva(s))",
                        System.currentTimeMillis() - t0, total);
            } while (completaPendiente.get());
        } finally {
            syncEnCurso.set(false);
        }
        return total;
    }

    /**
     * Importación PUNTUAL de una cuenta, pidiendo a Binance solo desde {@code desdeMs}.
     *
     * La dispara el poll cuando una orden sale del listado (se completó o se canceló). Es el
     * camino CRÍTICO: de esto depende que el saldo suba a los pocos segundos de liberar.
     *
     * NO usa el candado de la sync completa. Antes sí, y ahí estaba el problema: la sync completa
     * de 36 h se tomaba minutos, y mientras tanto CADA importación puntual se iba a la cola y
     * devolvía 0 ventas. En los logs se veía "COMPLETED pero su venta no quedó registrada" una y
     * otra vez, con el dinero sin llegar al saldo, aunque todo lo demás funcionara.
     *
     * Que corran a la vez es seguro: cada orden se guarda bajo su propio candado (ver
     * {@link #persistirConCandado}), existsByNumberOrder descarta las ya guardadas, y la columna
     * number_order es única en la base.
     */
    public int importarOrdenesRecientes(String cuentaBinance, long desdeMs, java.util.Set<String> ordenes) {
        if (cuentaBinance == null || cuentaBinance.isBlank()) return 0;
        java.util.Set<String> pedidas = ordenes != null ? new java.util.HashSet<>(ordenes) : java.util.Set.of();
        return ejecutarRapida(cuentaBinance, new Rapida(desdeMs, pedidas));
    }

    /** ¿La venta de esa orden ya quedó registrada? Lo usa el seguimiento para saber si puede soltarla. */
    public boolean ventaRegistrada(String orderNumber) {
        return orderNumber != null && !orderNumber.isBlank()
                && saleP2PRepository.existsByNumberOrder(orderNumber);
    }

    /**
     * Guarda UNA orden impidiendo que dos hilos la guarden a la vez (la sync completa y la
     * importación puntual pueden coincidir sobre la misma orden). El candado es POR ORDEN, así
     * que dos ventas distintas se guardan en paralelo sin estorbarse.
     */
    private boolean persistirConCandado(JsonNode obj, AccountBinance account, String orderNumber) {
        Object candado = candadosPorOrden.computeIfAbsent(orderNumber, k -> new Object());
        try {
            synchronized (candado) {
                return self.persistirVenta(obj, account);
            }
        } finally {
            candadosPorOrden.remove(orderNumber, candado);
        }
    }

    private int ejecutarCompleta() {
        List<AccountBinance> accounts = accountBinanceRepository.findByTipoAndActivaTrue("BINANCE");
        int totalNew = 0;

        for (AccountBinance account : accounts) {
            if (account.getApiKey() == null || account.getApiSecret() == null) continue;
            try {
                int newForAccount = self.syncAccount(account);
                if (newForAccount > 0) {
                    log.info("[Sync] {} → {} venta(s) P2P nueva(s)", account.getName(), newForAccount);
                }
                totalNew += newForAccount;
            } catch (Exception e) {
                log.warn("[Sync] Error en cuenta {}: {}", account.getName(), e.getMessage());
            }
        }
        return totalNew;
    }

    private int ejecutarRapida(String cuentaBinance, Rapida pedido) {
        AccountBinance account = accountBinanceRepository.findByName(cuentaBinance);
        if (account == null || account.getApiKey() == null || account.getApiSecret() == null) {
            log.warn("[Sync] Importación rápida: cuenta Binance '{}' no encontrada o sin llaves", cuentaBinance);
            return 0;
        }
        long t0 = System.currentTimeMillis();
        try {
            Map<String, String> estados = new HashMap<>();
            int nuevas = procesarRango(account, pedido.desdeMs(), Instant.now().toEpochMilli(), estados, false);
            log.info("[Sync] Importación rápida {} → {} venta(s) nueva(s) en {} ms",
                    cuentaBinance, nuevas, System.currentTimeMillis() - t0);
            resolverDesaparecidas(cuentaBinance, pedido.ordenes(), estados);
            return nuevas;
        } catch (Exception e) {
            log.warn("[Sync] Importación rápida falló en {}: {}", cuentaBinance, e.getMessage());
            // No se pierde: la próxima sync completa la vuelve a buscar.
            return 0;
        }
    }

    /**
     * Cierra el ciclo de cada orden que salió del listado activo: o su venta quedó registrada, o su
     * pre-asignación tiene que dejar de contar. Si no se hace, la fila se queda en la tabla y su
     * monto sigue sumando en el saldo amarillo de una cuenta que ya no tiene nada asignado a la vista.
     *
     * @param estados estado que Binance reportó para cada orden en la consulta que se acaba de hacer
     */
    private void resolverDesaparecidas(String cuentaBinance, java.util.Set<String> ordenes,
                                       Map<String, String> estados) {
        for (String orden : ordenes) {
            if (saleP2PRepository.existsByNumberOrder(orden)) continue;   // se importó: la pre ya se borró
            String estado = estados.get(orden);
            if (estado == null) {
                // Binance no la devolvió en su propia ventana de creación. No se borra por si acaso
                // (la limpieza conservadora de 48 h la barrerá), pero queda el aviso para revisarla.
                log.warn("[Sync] La orden {} ({}) salió del listado activo y Binance no la devolvió; "
                        + "su pre-asignación queda pendiente de revisión.", orden, cuentaBinance);
            } else if (estado.startsWith("CANCEL")) {
                self.limpiarPreAsignacionCancelada(orden);                // borra la pre: no habrá venta
            } else {
                // COMPLETED sin venta guardada (falló al persistir) o estado intermedio: se reintenta
                // en la próxima sync completa, así que la pre-asignación se conserva.
                log.info("[Sync] La orden {} ({}) salió del listado con estado {}; se reintentará en la próxima sync.",
                        orden, cuentaBinance, estado);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Sync por cuenta — lógica delta
    // ─────────────────────────────────────────────────────────────

    /**
     * Sincroniza una cuenta.
     *
     * IMPORTANTE — este método NO es @Transactional a propósito.
     *
     * Antes sí lo era, y la primera cosa que hacía dentro de la transacción era llamar a Binance
     * por HTTP (que además pagina). Eso dejaba una transacción abierta durante SEGUNDOS esperando
     * la red, y dentro de ella se borraban filas de p2p_pre_asignacion y se actualizaba el saldo
     * de account_cop.
     *
     * Mientras tanto, cuando el operador pre-asignaba una venta, su INSERT en p2p_pre_asignacion
     * necesitaba bloquear la fila de account_cop referenciada por la llave foránea — justo la que
     * el sync tenía tomada. El operador quedaba esperando hasta que vencía el tiempo límite del
     * candado y le salía "could not execute statement ... try restarting transaction".
     * Eso es lo que reportaban como "se demora mucho en asignar y después da error".
     *
     * Ahora: la lectura a Binance va FUERA de cualquier transacción, y cada orden se persiste en
     * su propia transacción corta. Los candados duran milisegundos en vez de segundos.
     */
    public int syncAccount(AccountBinance account) throws Exception {
        long endMs   = Instant.now().toEpochMilli();
        long startMs = resolveStartMs(account);

        int newCount = procesarRango(account, startMs, endMs, null, true);

        // Siempre actualiza el timestamp aunque no haya habido órdenes nuevas
        self.actualizarEstadoSync(account, endMs);
        return newCount;
    }

    /**
     * Lee de Binance las ventas de la cuenta creadas en [startMs, endMs] y guarda las completadas.
     * @param estadosVistos si no es null, se llena con el estado que Binance reportó para cada orden.
     * @param esCompleta    true si la llama la sync completa (la red de seguridad): cada venta que
     *                      registre es una que la importación rápida NO trajo, y se avisa en el log.
     */
    private int procesarRango(AccountBinance account, long startMs, long endMs,
                              Map<String, String> estadosVistos, boolean esCompleta) throws Exception {
        // ── 1) LECTURA a Binance, sin transacción abierta ──
        long t0 = System.currentTimeMillis();
        String json = binanceService.getP2POrdersInRange(account.getName(), startMs, endMs, "SELL");
        long msBinance = System.currentTimeMillis() - t0;
        JsonNode root = mapper.readTree(json);

        if (root.has("error")) {
            log.warn("[Sync] Binance error en {}: {}", account.getName(), root.get("error").asText());
            return 0;
        }

        JsonNode data = root.path("data");
        int newCount = 0;

        // ── 2) ESCRITURA: una transacción corta por orden ──
        // Si una orden falla, las demás igual se guardan (antes se perdía el lote completo).
        if (data.isArray()) {
            // Antes se preguntaba a la base orden por orden (2–3 consultas cada una, incluso por las
            // ya registradas). Con mucho movimiento cada importación tardaba 15–48 s y el poll,
            // que es quien la corre, dejaba de vigilar mientras tanto: las cancelaciones tardaban
            // minutos en quitarse del amarillo. Ahora se pregunta UNA vez por lote qué ventas ya
            // están registradas (y con qué comisión) y qué canceladas tienen pre-asignación, y
            // solo se toca la base por las órdenes que de verdad tienen algo que hacer.
            Set<String> numeros = new HashSet<>();
            for (JsonNode obj : data) {
                String on = obj.path("orderNumber").asText("");
                if (!on.isBlank()) numeros.add(on);
            }
            Map<String, Double> registradas = new HashMap<>();
            Set<String> conPreAsignacion = new HashSet<>();
            List<String> lista = new ArrayList<>(numeros);
            for (int i = 0; i < lista.size(); i += 500) {
                List<String> lote = lista.subList(i, Math.min(i + 500, lista.size()));
                for (Object[] fila : saleP2PRepository.findComisionesByNumberOrders(lote)) {
                    registradas.put((String) fila[0], fila[1] != null ? ((Number) fila[1]).doubleValue() : 0.0);
                }
                conPreAsignacion.addAll(preAsignacionRepository.findOrderNumbersIn(lote));
            }

            for (JsonNode obj : data) {
                String orderNumber = obj.path("orderNumber").asText();
                if (estadosVistos != null) {
                    estadosVistos.put(orderNumber, obj.path("orderStatus").asText("").toUpperCase());
                }
                if (isCanceledSell(obj)) {
                    // Solo hay algo que hacer si tiene pre-asignación que borrar, o si se registró
                    // como venta (aviso REVISAR). Las demás canceladas no tocan la base.
                    if (!conPreAsignacion.contains(orderNumber) && !registradas.containsKey(orderNumber)) continue;
                    try {
                        self.limpiarPreAsignacionCancelada(obj.path("orderNumber").asText());
                    } catch (Exception e) {
                        log.warn("[Sync] No se pudo limpiar la pre-asignación de la orden cancelada {}: {}",
                                obj.path("orderNumber").asText(), e.getMessage());
                    }
                    continue;
                }
                if (!isValidSell(obj)) continue;
                Double comisionGuardada = registradas.get(orderNumber);
                if (comisionGuardada != null) {
                    // Ya registrada. Solo se vuelve a tocar si se completó con una comisión distinta
                    // a la guardada (se guardó al liberar); persistirVenta hace ese ajuste.
                    boolean comisionCambio = "COMPLETED".equalsIgnoreCase(obj.path("orderStatus").asText(""))
                            && Math.abs(comisionDe(obj) - comisionGuardada) >= 0.000001;
                    if (!comisionCambio) continue;
                }
                try {
                    if (persistirConCandado(obj, account, orderNumber)) {
                        newCount++;
                        if (esCompleta) {
                            long minutos = (System.currentTimeMillis() - obj.path("createTime").asLong(0)) / 60_000;
                            log.warn("[Sync] RED DE SEGURIDAD registró la venta {} ({}, estado {}, creada hace {} min) "
                                    + "— la importación rápida no la trajo.",
                                    obj.path("orderNumber").asText(), account.getName(),
                                    obj.path("orderStatus").asText(""), minutos);
                        }
                    }
                } catch (Exception e) {
                    log.warn("[Sync] No se pudo guardar la orden {} ({}): {}",
                            obj.path("orderNumber").asText(), account.getName(), e.getMessage());
                }
            }
        }
        {
            // Medición: dice si la lentitud (de la completa o de la rápida) está en Binance o en la base.
            log.info("[Sync] {} {}: {} orden(es) de Binance en {} ms, revisión/guardado en {} ms, {} nueva(s)",
                    esCompleta ? "Completa" : "Rápida", account.getName(),
                    data.isArray() ? data.size() : 0, msBinance,
                    System.currentTimeMillis() - t0 - msBinance, newCount);
        }
        return newCount;
    }

    /**
     * Una orden CANCELADA nunca va a importarse, así que su pre-asignación ya no tiene razón de
     * ser. Se borra en cuanto se ve la cancelación: si no, su monto seguiría sumando en el
     * verde/amarillo de la cuenta COP hasta que la limpieza de 48 h la encontrara.
     */
    @Transactional
    public void limpiarPreAsignacionCancelada(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) return;
        // Caso raro pero importante: la venta se registró al liberarse (DISTRIBUTING) y después
        // Binance terminó cancelándola. La plata ya se sumó a la cuenta COP, así que hay que
        // revisarla a mano — no se deshace sola porque, si el comprador sí pagó, el saldo está bien.
        if (saleP2PRepository.existsByNumberOrder(orderNumber)) {
            // Una sola vez por orden: el sync repasa la misma ventana cada minuto.
            if (canceladasYaAvisadas.add(orderNumber)) {
                log.warn("[Sync] REVISAR: la orden {} se registró como venta y Binance la reporta CANCELADA. "
                        + "Verificar si la plata entró de verdad a la cuenta COP.", orderNumber);
            }
            return;
        }

        preAsignacionRepository.findByOrderNumber(orderNumber).ifPresent(pre -> {
            preAsignacionRepository.delete(pre);
            log.info("[PreAsign] Orden {} cancelada → pre-asignación eliminada", orderNumber);
            com.binance.web.BinanceAPI.AccountCopSaldoListener.notificarTrasCommit();
        });
    }

    /**
     * Carrera: el operador pre-asigna JUSTO después de que la venta ya se importó sin asignar.
     * Antes la pre-asignación quedaba huérfana y la venta sin cuenta (plata en el banco pero no en
     * el saldo). Ahora, si la venta ya existe sin asignar, se le aplica la pre-asignación en el acto.
     * Debe llamarse DENTRO de la transacción que guardó la pre-asignación.
     */
    @Transactional
    public void aplicarPreAsignacionSiYaSeImporto(String orderNumber) {
        saleP2PRepository.findFirstByNumberOrder(orderNumber).ifPresent(sale -> {
            if (Boolean.TRUE.equals(sale.getAsignado())) {
                // Ya se asignó por otro lado: la pre-asignación sobra.
                preAsignacionRepository.deleteByOrderNumber(orderNumber);
                log.info("[PreAsign] Orden {} ya estaba importada y asignada → pre-asignación descartada", orderNumber);
                return;
            }
            autoAssign(sale);
        });
    }

    /**
     * Guarda UNA venta y le aplica su pre-asignación, en una transacción propia y corta.
     * Devuelve true si se guardó (false si ya existía).
     */
    @Transactional
    public boolean persistirVenta(JsonNode obj, AccountBinance account) {
        String orderNumber = obj.path("orderNumber").asText();
        if (orderNumber.isBlank()) return false;
        if (saleP2PRepository.existsByNumberOrder(orderNumber)) {
            // Ya estaba registrada. Si se guardó al liberar (DISTRIBUTING) y Binance ya la cerró,
            // puede haber quedado una comisión definitiva distinta: se corrige acá.
            if ("COMPLETED".equalsIgnoreCase(obj.path("orderStatus").asText(""))) {
                ajustarComisionSiCambio(orderNumber, obj);
            }
            return false;
        }

        SaleP2P sale = buildSale(obj, account);
        saleP2PRepository.save(sale);
        try {
            autoAssign(sale);
        } catch (Exception e) {
            log.warn("[Sync] Auto-asignación falló para orden {} ({}): {}",
                    orderNumber, account.getName(), e.getMessage());
        }
        return true;
    }

    /**
     * Corrige la comisión de una venta que se guardó al liberarse (DISTRIBUTING) si, al completarse,
     * Binance reporta otra. La comisión entra en el cálculo de la utilidad, así que se recalcula.
     * No toca el saldo COP: los pesos de la venta no cambian entre liberar y completar.
     */
    private void ajustarComisionSiCambio(String orderNumber, JsonNode obj) {
        saleP2PRepository.findFirstByNumberOrder(orderNumber).ifPresent(sale -> {
            double comisionFinal = comisionDe(obj);
            double comisionGuardada = sale.getCommission() != null ? sale.getCommission() : 0.0;
            if (Math.abs(comisionFinal - comisionGuardada) < 0.000001) return;
            sale.setCommission(comisionFinal);
            utilidadCalculator.calcularYAsignar(sale);
            saleP2PRepository.save(sale);
            log.info("[Sync] Comisión de la venta {} corregida al completarse: {} → {}",
                    orderNumber, comisionGuardada, comisionFinal);
        });
    }

    /** Marca de tiempo de la última sync, en su propia transacción. */
    @Transactional
    public void actualizarEstadoSync(AccountBinance account, long endMs) {
        updateSyncState(account, endMs);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Límite inferior de la ventana que se le pide a Binance.
     *
     * IMPORTANTE: No usar lastSyncAtMs. Una orden puede crearse en TRADING (T1) y completarse
     * después del último sync (T2). Si usáramos T2, Binance filtraría por createTime >= T2 y
     * nunca devolvería esa orden, porque su createTime es T1 < T2.
     *
     * Tampoco sirve el inicio del día: Binance filtra por FECHA DE CREACIÓN, no de completado.
     * Una orden creada a las 11:50 p.m. y completada a las 12:10 a.m. tiene createTime de AYER,
     * así que con una ventana "desde hoy 00:00" no se importaba nunca: el dinero entraba a la
     * cuenta COP pero la venta no quedaba registrada y su pre-asignación quedaba huérfana.
     * Por eso se mira hacia atrás {@code p2p.sync.lookback-horas} (36 h por defecto), que cubre
     * de sobra el cruce de medianoche y cualquier orden que se demore en completarse.
     *
     * Repetir órdenes ya importadas no cuesta nada: existsByNumberOrder las descarta antes de
     * guardar, así que ampliar la ventana es seguro.
     */
    private long resolveStartMs(AccountBinance account) {
        long ahora = Instant.now().toEpochMilli();
        long desdeVentana = ahora - (lookbackHoras * 3_600_000L);
        // Nunca antes del inicio del día de hace {lookbackHoras}: mantiene la ventana acotada
        // y alineada a días completos, para no pedirle a Binance rangos innecesariamente largos.
        long inicioDeHoy = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant().toEpochMilli();
        return Math.min(desdeVentana, inicioDeHoy);
    }

    /**
     * Filtra las ventas USDT que ya se pueden registrar.
     *
     * COMPLETED y DISTRIBUTING cuentan por igual, por decisión del cliente. DISTRIBUTING es el
     * instante después de liberar, mientras Binance entrega el cripto: en ese punto el comprador YA
     * pagó (por eso se liberó) y la plata YA está en la cuenta COP, así que registrarla ahí es más
     * fiel a la realidad que esperar. Además evita el hueco donde la orden no estaba ni en curso ni
     * completada, que dejaba el monto pegado en el saldo amarillo.
     */
    private boolean isValidSell(JsonNode obj) {
        String estado = obj.path("orderStatus").asText("");
        return ("COMPLETED".equalsIgnoreCase(estado) || "DISTRIBUTING".equalsIgnoreCase(estado))
                && "SELL".equalsIgnoreCase(obj.path("tradeType").asText(""))
                && "USDT".equalsIgnoreCase(obj.path("asset").asText(""));
    }

    /** Comisión de la orden, en MILES (la misma escala que dollarsUs). */
    private double comisionDe(JsonNode obj) {
        return (!obj.path("takerCommission").isNull()
                ? obj.path("takerCommission").asDouble(0.0)
                : obj.path("commission").asDouble(0.0)) / 1_000.0;
    }

    /** Venta USDT cancelada (Binance usa CANCELLED y CANCELLED_BY_SYSTEM). */
    private boolean isCanceledSell(JsonNode obj) {
        return obj.path("orderStatus").asText("").toUpperCase().startsWith("CANCEL")
                && "SELL".equalsIgnoreCase(obj.path("tradeType").asText(""))
                && "USDT".equalsIgnoreCase(obj.path("asset").asText(""));
    }

    private SaleP2P buildSale(JsonNode obj, AccountBinance account) {
        double pesosCopRaw = obj.path("totalPrice").asDouble(0.0);
        double pesosCop    = pesosCopRaw / 1_000.0;
        double dollarsUs   = obj.path("amount").asDouble(0.0) / 1_000.0;
        double tasa        = obj.path("unitPrice").asDouble(0.0);
        // La comisión son USDT, igual que dollarsUs, así que va en la MISMA escala (miles).
        // Antes se guardaba cruda: la utilidad hace (dólares + comisión) × tasa, o sea que una
        // comisión cruda pesaba mil veces de más dentro del costo. No se había notado porque
        // todas las ventas revisadas traen comisión 0 — pero en cuanto Binance cobre una, la
        // utilidad de esa venta se iría al piso sin motivo.
        double commission  = comisionDe(obj);

        SaleP2P sale = new SaleP2P();
        sale.setNumberOrder(obj.path("orderNumber").asText());
        sale.setDate(Instant.ofEpochMilli(obj.path("createTime").asLong()).atZone(ZONE).toLocalDateTime());
        sale.setPesosCop(pesosCop);
        sale.setDollarsUs(dollarsUs);
        sale.setCommission(commission);
        sale.setTasa(tasa);
        sale.setBinanceAccount(account);
        sale.setAsignado(false);
        sale.setUtilidad(0.0);
        sale.setImportadoEn(LocalDateTime.now(ZONE));   // para medir el retraso de la importación
        return sale;
    }

    /**
     * Aplica la pre-asignación manual del operador a la venta recién importada
     * (tabla p2p_pre_asignacion). Si no hay pre-asignación, la venta queda sin asignar
     * y se asigna manualmente después.
     */
    private void autoAssign(SaleP2P sale) {
        Optional<P2PPreAsignacion> pre =
                preAsignacionRepository.findByOrderNumber(sale.getNumberOrder());

        if (pre.isPresent()) {
            AccountCop cop = pre.get().getCuentaCop();
            applyAssignment(sale, cop);
            // Eliminar la pre-asignación: ya cumplió su función
            preAsignacionRepository.deleteByOrderNumber(sale.getNumberOrder());
            log.info("[PreAsign] Venta {} → {} (pre-asignación manual)", sale.getNumberOrder(), cop.getName());
        }
    }

    /** Aplica el detalle de asignación a la venta y actualiza saldos. */
    private void applyAssignment(SaleP2P sale, AccountCop cop) {
        double amount = sale.getPesosCop() != null ? sale.getPesosCop() : 0.0;

        SaleP2pAccountCop detail = new SaleP2pAccountCop();
        detail.setSaleP2p(sale);
        detail.setAmount(amount);
        detail.setNameAccount(cop.getName());
        detail.setAccountCop(cop);

        cop.setBalance((cop.getBalance() != null ? cop.getBalance() : 0.0) + amount);
        cop.setCupoDisponibleHoy(
                (cop.getCupoDisponibleHoy() != null ? cop.getCupoDisponibleHoy() : 0.0) - amount);
        accountCopService.saveAccountCopSafe(cop);

        // (Se quitó el descuento del USDT en el saldo interno: ese saldo ya no se lleva.
        //  El USDT vendido se refleja solo en Binance, que es de donde se lee todo ahora.)

        if (sale.getAccountCopsDetails() == null) sale.setAccountCopsDetails(new ArrayList<>());
        sale.getAccountCopsDetails().add(detail);
        sale.setAsignado(true);
        // La utilidad se calcula acá, con los detalles ya cargados. Antes las ventas que entraban
        // por pre-asignación quedaban siempre en utilidad = 0, que es justo el camino normal hoy.
        utilidadCalculator.calcularYAsignar(sale);
        saleP2PRepository.save(sale);
    }

    private void updateSyncState(AccountBinance account, long timestampMs) {
        P2PSyncState state = syncStateRepository.findByBinanceAccount_Name(account.getName())
                .orElse(new P2PSyncState());
        state.setBinanceAccount(account);
        state.setLastSyncAtMs(timestampMs);
        state.setLastSyncTime(LocalDateTime.now(ZONE));
        syncStateRepository.save(state);
    }

    /** Devuelve el estado de sync de todas las cuentas (útil para debug/monitoreo). */
    public List<P2PSyncState> getAllSyncStates() {
        return syncStateRepository.findAll();
    }
}
