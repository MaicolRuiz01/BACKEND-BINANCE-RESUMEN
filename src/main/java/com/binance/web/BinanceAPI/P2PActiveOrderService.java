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

    /** Estados de Binance que consideramos "activos" (no terminales). */
    private static final Set<String> ACTIVE_STATUSES = Set.of("TRADING", "BUYER_PAYED", "PENDING");

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

    // ─────────────────────────────────────────────────────────────
    // Consulta principal
    // ─────────────────────────────────────────────────────────────

    /**
     * Retorna todas las órdenes P2P activas de todas las cuentas Binance,
     * enriquecidas con la pre-asignación si existe.
     */
    public List<ActiveP2POrderDto> getAllActiveOrders() {
        List<AccountBinance> accounts = accountBinanceRepository.findByTipoAndActivaTrue("BINANCE");
        List<ActiveP2POrderDto> result = new ArrayList<>();

        for (AccountBinance account : accounts) {
            if (account.getApiKey() == null || account.getApiSecret() == null) continue;
            try {
                result.addAll(getActiveOrdersForAccount(account.getName()));
            } catch (Exception e) {
                log.warn("[ActiveOrders] Error en cuenta {}: {}", account.getName(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * Retorna órdenes activas de una cuenta específica.
     */
    public List<ActiveP2POrderDto> getActiveOrdersForAccount(String accountName) throws Exception {
        // Ventana: últimas 48h (suficiente para cualquier orden abierta)
        long endMs   = Instant.now().toEpochMilli();
        long startMs = endMs - (48L * 60 * 60 * 1000);

        String json   = binanceService.getP2POrdersInRange(accountName, startMs, endMs, "SELL");
        JsonNode root = mapper.readTree(json);

        if (root.has("error")) {
            log.warn("[ActiveOrders] Binance error {}: {}", accountName, root.get("error").asText());
            return List.of();
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
     */
    public List<ActiveP2POrderDto> detectStatusChanges() {
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

        // Limpiar órdenes que ya no están activas del cache
        lastKnownStatus.keySet().retainAll(currentOrderNumbers);

        return changed;
    }

    public List<ActiveP2POrderDto> getLastKnownActiveOrders() {
        return getAllActiveOrders(); // siempre fresco desde Binance
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
        Optional<P2PPreAsignacion> pre = preAsignacionRepository.findByOrderNumber(orderNumber);
        if (pre.isPresent()) {
            copId     = pre.get().getCuentaCop().getId();
            copNombre = pre.get().getCuentaCop().getName();
        }

        return new ActiveP2POrderDto(
                orderNumber, status, statusLabel(status),
                accountName, dollarsUs, pesosCop, tasa, createTime,
                copId, copNombre
        );
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "TRADING"     -> "En curso";
            case "BUYER_PAYED" -> "Pago recibido";
            case "PENDING"     -> "Pendiente";
            default            -> status;
        };
    }
}
