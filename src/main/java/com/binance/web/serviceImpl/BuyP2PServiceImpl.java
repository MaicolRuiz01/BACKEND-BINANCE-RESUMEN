package com.binance.web.serviceImpl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BuyP2P;
import com.binance.web.Entity.BuyP2pAccountCop;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyP2PRepository;
import com.binance.web.model.AssignAccountDto;
import com.binance.web.model.BuyP2PDto;
import com.binance.web.service.AccountCopService;
import com.binance.web.service.BuyP2PService;

@Slf4j
@Service
public class BuyP2PServiceImpl implements BuyP2PService {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    @Autowired private BuyP2PRepository buyP2PRepository;
    @Autowired private AccountCopService accountCopService;
    @Autowired private BinanceService binanceService;
    @Autowired private AccountBinanceRepository accountBinanceRepository;

    // =========================
    // 1) IMPORTA Y GUARDA HOY
    // =========================
    private void createBuyP2pDirectly(String account, LocalDate today) {
        try {
            long start = today.atStartOfDay(ZONE_BOGOTA).toInstant().toEpochMilli();
            long end   = today.plusDays(1).atStartOfDay(ZONE_BOGOTA).toInstant().toEpochMilli();

            String jsonResponse = binanceService.getP2POrdersInRange(account, start, end, "BUY");
            JsonNode root = mapper.readTree(jsonResponse);

            if (root.has("error")) {
                log.error("Error Binance ({}): {}", account, root.get("error").asText());
                return;
            }

            LocalDateTime inicio = today.atStartOfDay();
            LocalDateTime fin    = inicio.plusDays(1);

            for (JsonNode obj : root.path("data")) {
                String status    = obj.path("orderStatus").asText("");
                String tradeType = obj.path("tradeType").asText("");
                String asset     = obj.path("asset").asText("");

                if (!"COMPLETED".equalsIgnoreCase(status))  continue;
                if (!"BUY".equalsIgnoreCase(tradeType))     continue;
                if (!"USDT".equalsIgnoreCase(asset))        continue;

                String orderNumber    = obj.path("orderNumber").asText();
                LocalDateTime fechaOrden = convertTimestamp(obj.path("createTime").asLong());

                if (fechaOrden.isBefore(inicio) || !fechaOrden.isBefore(fin)) continue;
                if (buyP2PRepository.existsByNumberOrder(orderNumber))         continue;

                double pesosCopRaw = obj.path("totalPrice").asDouble(0.0);

                BuyP2P buy = new BuyP2P();
                buy.setNumberOrder(orderNumber);
                buy.setDate(fechaOrden);
                buy.setPesosCop(pesosCopRaw / 1_000.0); // convertir a miles de COP
                buy.setDollarsUs(obj.path("amount").asDouble(0.0));

                double commission = !obj.path("takerCommission").isNull()
                        ? obj.path("takerCommission").asDouble(0.0)
                        : obj.path("commission").asDouble(0.0);
                buy.setCommission(commission);
                // tasa calculada sobre monto real para mantener tipo de cambio legible
                buy.setTasa(buy.getDollarsUs() > 0 ? pesosCopRaw / buy.getDollarsUs() : 0.0);

                AccountBinance ab = accountBinanceRepository.findByName(account);
                buy.setBinanceAccount(ab);
                buy.setAsignado(false);
                buy.setUtilidad(0.0);
                buyP2PRepository.save(buy);
            }
        } catch (Exception e) {
            log.error("Error en createBuyP2pDirectly ({}): {}", account, e.getMessage(), e);
        }
    }

    private LocalDateTime convertTimestamp(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZONE_BOGOTA).toLocalDateTime();
    }

    private Double calculateTasa(BuyP2P buy) {
        if (buy.getDollarsUs() == null || buy.getDollarsUs() == 0.0) return 0.0;
        return buy.getPesosCop() / buy.getDollarsUs();
    }

    // =========================
    // 2) LISTA NO ASIGNADAS HOY
    // =========================
    @Override
    public List<BuyP2PDto> getTodayNoAsignadas(String account) {
        LocalDate today     = LocalDate.now(ZONE_BOGOTA);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end   = start.plusDays(1);

        createBuyP2pDirectly(account, today);

        AccountBinance ab = accountBinanceRepository.findByName(account);
        if (ab == null) return List.of();
        return buyP2PRepository.findNoAsignadasByAccountAndDateBetween(ab.getId(), start, end)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<BuyP2PDto> getTodayNoAsignadasAllAccounts() {
        LocalDate today     = LocalDate.now(ZONE_BOGOTA);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end   = start.plusDays(1);

        List<BuyP2PDto> out = new ArrayList<>();

        for (AccountBinance acc : accountBinanceRepository.findByTipo("BINANCE")) {
            createBuyP2pDirectly(acc.getName(), today);
            buyP2PRepository.findNoAsignadasByAccountAndDateBetween(acc.getId(), start, end)
                    .forEach(b -> out.add(toDto(b)));
        }

        return out;
    }

    // =========================
    // 3) ASIGNACIÓN CUENTAS COP
    // =========================
    @Override
    public String processAssignAccountCop(Integer buyId, List<AssignAccountDto> accounts) {
        BuyP2P buy = buyP2PRepository.findById(buyId).orElse(null);
        if (buy == null)                               return "No se encontró la compra con id " + buyId;
        if (Boolean.TRUE.equals(buy.getAsignado()))    return "Compra ya fue asignada";

        if (buy.getAccountCopsDetails() == null) {
            buy.setAccountCopsDetails(new ArrayList<>());
        } else {
            buy.getAccountCopsDetails().forEach(d -> d.setBuyP2p(null));
            buy.getAccountCopsDetails().clear();
        }

        double total = 0.0;

        for (AssignAccountDto dto : accounts) {
            BuyP2pAccountCop det = new BuyP2pAccountCop();
            det.setBuyP2p(buy);
            det.setAmount(dto.getAmount());
            det.setNameAccount(dto.getNameAccount());

            if (dto.getAccountCop() != null) {
                AccountCop acc  = accountCopService.findByIdAccountCop(dto.getAccountCop());
                double current  = acc.getBalance() != null ? acc.getBalance() : 0.0;
                double monto    = dto.getAmount() != null ? dto.getAmount() : 0.0;
                acc.setBalance(current - monto);
                accountCopService.saveAccountCop(acc);
                det.setAccountCop(acc);
            }

            total += dto.getAmount() != null ? dto.getAmount() : 0.0;
            buy.getAccountCopsDetails().add(det);
        }

        if (buy.getPesosCop() != null && total > buy.getPesosCop()) {
            return "El total asignado excede el monto COP de la compra.";
        }

        buy.setAsignado(true);
        if (buy.getUtilidad() == null) buy.setUtilidad(0.0);
        buyP2PRepository.save(buy);
        return "Asignación realizada con éxito";
    }

    private BuyP2PDto toDto(BuyP2P b) {
        BuyP2PDto dto = new BuyP2PDto();
        dto.setId(b.getId());
        dto.setNumberOrder(b.getNumberOrder());
        dto.setDate(b.getDate());
        dto.setCommission(b.getCommission());
        dto.setPesosCop(b.getPesosCop());
        dto.setDollarsUs(b.getDollarsUs());
        dto.setTasa(b.getTasa());
        dto.setAsignado(b.getAsignado());
        dto.setNameAccountBinance(b.getBinanceAccount() != null ? b.getBinanceAccount().getName() : null);
        return dto;
    }
}
