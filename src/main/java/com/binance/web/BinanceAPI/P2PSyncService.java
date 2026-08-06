package com.binance.web.BinanceAPI;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /** Referencia a sí mismo (vía proxy) para que @Transactional de syncAccount SÍ aplique
     *  cuando se llama desde syncAllAccounts (evita el problema de auto-invocación de Spring). */
    @Autowired @Lazy private P2PSyncService self;

    // ─────────────────────────────────────────────────────────────
    // Punto de entrada principal
    // ─────────────────────────────────────────────────────────────

    /**
     * Sincroniza todas las cuentas Binance registradas.
     * @return número total de ventas P2P nuevas encontradas y guardadas
     */
    public int syncAllAccounts() {
        // Si ya hay una sincronización en curso, no arrancamos otra (evita la carrera
        // que producía "Duplicate entry" al insertar la misma orden dos veces).
        if (!syncEnCurso.compareAndSet(false, true)) {
            log.debug("[Sync] Ya hay una sincronización en curso; se omite esta ejecución.");
            return 0;
        }
        try {
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
        } finally {
            syncEnCurso.set(false);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Sync por cuenta — lógica delta
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public int syncAccount(AccountBinance account) throws Exception {
        long endMs   = Instant.now().toEpochMilli();
        long startMs = resolveStartMs(account);

        // Pide a Binance SOLO las órdenes desde la última sync
        String json = binanceService.getP2POrdersInRange(account.getName(), startMs, endMs, "SELL");
        JsonNode root = mapper.readTree(json);

        if (root.has("error")) {
            log.warn("[Sync] Binance error en {}: {}", account.getName(), root.get("error").asText());
            return 0;
        }

        JsonNode data = root.path("data");
        int newCount = 0;

        if (data.isArray()) {
            for (JsonNode obj : data) {
                if (!isValidSell(obj)) continue;

                String orderNumber = obj.path("orderNumber").asText();
                if (orderNumber.isBlank()) continue;
                if (saleP2PRepository.existsByNumberOrder(orderNumber)) continue;

                SaleP2P sale = buildSale(obj, account);
                saleP2PRepository.save(sale);
                try {
                    autoAssign(sale);
                } catch (Exception e) {
                    log.warn("[Sync] Auto-asignación falló para orden {} ({}): {}",
                            orderNumber, account.getName(), e.getMessage());
                }
                newCount++;
            }
        }

        // Siempre actualiza el timestamp aunque no haya habido órdenes nuevas
        updateSyncState(account, endMs);
        return newCount;
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

    /** Filtra: solo ventas USDT completadas. */
    private boolean isValidSell(JsonNode obj) {
        return "COMPLETED".equalsIgnoreCase(obj.path("orderStatus").asText(""))
                && "SELL".equalsIgnoreCase(obj.path("tradeType").asText(""))
                && "USDT".equalsIgnoreCase(obj.path("asset").asText(""));
    }

    private SaleP2P buildSale(JsonNode obj, AccountBinance account) {
        double pesosCopRaw = obj.path("totalPrice").asDouble(0.0);
        double pesosCop    = pesosCopRaw / 1_000.0;
        double dollarsUs   = obj.path("amount").asDouble(0.0) / 1_000.0;
        double tasa        = obj.path("unitPrice").asDouble(0.0);
        double commission  = !obj.path("takerCommission").isNull()
                ? obj.path("takerCommission").asDouble(0.0)
                : obj.path("commission").asDouble(0.0);

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

        if (sale.getDollarsUs() != null && sale.getDollarsUs() > 0) {
            accountBinanceService.subtractBalance(sale.getBinanceAccount().getName(), sale.getDollarsUs());
        }

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
