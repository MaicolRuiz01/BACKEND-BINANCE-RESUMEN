package com.binance.web.BinanceAPI;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.binance.web.Entity.*;
import com.binance.web.Repository.*;
import com.binance.web.service.AccountBinanceService;
import com.binance.web.service.AccountCopService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired private BinanceService binanceService;
    @Autowired private SaleP2PRepository saleP2PRepository;
    @Autowired private AccountBinanceRepository accountBinanceRepository;
    @Autowired private P2PSyncStateRepository syncStateRepository;
    @Autowired private P2PAssignmentRuleRepository assignmentRuleRepository;
    @Autowired private AccountCopService accountCopService;
    @Autowired private AccountBinanceService accountBinanceService;

    // ─────────────────────────────────────────────────────────────
    // Punto de entrada principal
    // ─────────────────────────────────────────────────────────────

    /**
     * Sincroniza todas las cuentas Binance registradas.
     * @return número total de ventas P2P nuevas encontradas y guardadas
     */
    public int syncAllAccounts() {
        List<AccountBinance> accounts = accountBinanceRepository.findByTipo("BINANCE");
        int totalNew = 0;

        for (AccountBinance account : accounts) {
            if (account.getApiKey() == null || account.getApiSecret() == null) continue;
            try {
                int newForAccount = syncAccount(account);
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
     * Devuelve el timestamp de inicio del siguiente query a Binance.
     * - Si hay estado guardado → usa ese timestamp (delta real)
     * - Si es la primera vez → usa el inicio del día actual en Bogotá
     */
    private long resolveStartMs(AccountBinance account) {
        return syncStateRepository.findByBinanceAccount_Name(account.getName())
                .map(P2PSyncState::getLastSyncAtMs)
                .orElseGet(() -> LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant().toEpochMilli());
    }

    /** Filtra: solo ventas USDT completadas. */
    private boolean isValidSell(JsonNode obj) {
        return "COMPLETED".equalsIgnoreCase(obj.path("orderStatus").asText(""))
                && "SELL".equalsIgnoreCase(obj.path("tradeType").asText(""))
                && "USDT".equalsIgnoreCase(obj.path("asset").asText(""));
    }

    private SaleP2P buildSale(JsonNode obj, AccountBinance account) {
        double pesosCop  = obj.path("totalPrice").asDouble(0.0);
        double dollarsUs = obj.path("amount").asDouble(0.0);
        double commission = !obj.path("takerCommission").isNull()
                ? obj.path("takerCommission").asDouble(0.0)
                : obj.path("commission").asDouble(0.0);

        SaleP2P sale = new SaleP2P();
        sale.setNumberOrder(obj.path("orderNumber").asText());
        sale.setDate(Instant.ofEpochMilli(obj.path("createTime").asLong()).atZone(ZONE).toLocalDateTime());
        sale.setPesosCop(pesosCop);
        sale.setDollarsUs(dollarsUs);
        sale.setCommission(commission);
        sale.setTasa(dollarsUs > 0 ? pesosCop / dollarsUs : 0.0);
        sale.setBinanceAccount(account);
        sale.setAsignado(false);
        sale.setUtilidad(0.0);
        return sale;
    }

    /** Aplica la regla de auto-asignación si está activa para esta cuenta. */
    private void autoAssign(SaleP2P sale) {
        assignmentRuleRepository
                .findByBinanceAccount_Name(sale.getBinanceAccount().getName())
                .filter(r -> Boolean.TRUE.equals(r.getActive()))
                .ifPresent(rule -> {
                    AccountCop cop = rule.getCopAccount();
                    double amount  = sale.getPesosCop() != null ? sale.getPesosCop() : 0.0;

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
                    saleP2PRepository.save(sale);

                    log.info("[AutoAssign] Venta {} → {} ({} COP)",
                            sale.getNumberOrder(), cop.getName(), amount);
                });
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
