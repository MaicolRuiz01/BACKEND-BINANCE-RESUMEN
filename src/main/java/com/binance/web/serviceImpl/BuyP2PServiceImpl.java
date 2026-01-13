package com.binance.web.serviceImpl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Service
public class BuyP2PServiceImpl implements BuyP2PService {

    @Autowired private BuyP2PRepository buyP2PRepository;
    @Autowired private AccountCopService accountCopService;
    @Autowired private BinanceService binanceService;
    @Autowired private AccountBinanceRepository accountBinanceRepository;

    // =========================
    // 1) IMPORTA Y GUARDA HOY
    // =========================
    private void createBuyP2pDirectly(String account, LocalDate today) {
        String jsonResponse = binanceService.getP2POrderLatest(account);
        JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

        if (jsonObject.has("error")) {
            System.err.println("Error Binance: " + jsonObject.get("error").getAsString());
            return;
        }

        JsonArray dataArray = jsonObject.getAsJsonArray("data");
        if (dataArray == null) return;

        LocalDateTime inicio = today.atStartOfDay();
        LocalDateTime fin = inicio.plusDays(1);

        for (JsonElement element : dataArray) {
            JsonObject obj = element.getAsJsonObject();

            String status = obj.has("orderStatus") ? obj.get("orderStatus").getAsString() : "";
            String tradeType = obj.has("tradeType") ? obj.get("tradeType").getAsString() : "";
            String asset = obj.has("asset") ? obj.get("asset").getAsString() : "";

            if (!"COMPLETED".equalsIgnoreCase(status)) continue;
            if (!"BUY".equalsIgnoreCase(tradeType)) continue;
            if (!"USDT".equalsIgnoreCase(asset)) continue;

            String orderNumber = obj.get("orderNumber").getAsString();
            LocalDateTime fechaOrden = convertTimestamp(obj.get("createTime").getAsLong());

            if (fechaOrden.isBefore(inicio) || !fechaOrden.isBefore(fin)) continue;
            if (buyP2PRepository.existsByNumberOrder(orderNumber)) continue;

            BuyP2P buy = new BuyP2P();
            buy.setNumberOrder(orderNumber);
            buy.setDate(fechaOrden);

            // COP pagados (en tu JSON real es totalPrice)
            Double pesosCop = obj.has("totalPrice") && !obj.get("totalPrice").isJsonNull()
                    ? obj.get("totalPrice").getAsDouble()
                    : 0.0;

            // USDT comprados
            Double dollarsUs = obj.has("amount") && !obj.get("amount").isJsonNull()
                    ? obj.get("amount").getAsDouble()
                    : 0.0;

            // comisión real suele venir en takerCommission
            Double commission = obj.has("takerCommission") && !obj.get("takerCommission").isJsonNull()
                    ? obj.get("takerCommission").getAsDouble()
                    : (
                        obj.has("commission") && !obj.get("commission").isJsonNull()
                            ? obj.get("commission").getAsDouble()
                            : 0.0
                    );

            buy.setPesosCop(pesosCop);
            buy.setDollarsUs(dollarsUs);
            buy.setCommission(commission);

            buy.setTasa(calculateTasa(buy));

            AccountBinance ab = accountBinanceRepository.findByName(account);
            buy.setBinanceAccount(ab);

            buy.setAsignado(false);
            buy.setUtilidad(0.0);

            buyP2PRepository.save(buy);
        }
    }

    private LocalDateTime convertTimestamp(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.of("America/Bogota"))
                .toLocalDateTime();
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
        LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        createBuyP2pDirectly(account, today);

        AccountBinance ab = accountBinanceRepository.findByName(account);
        if (ab == null) return List.of();

        List<BuyP2P> buys = buyP2PRepository.findNoAsignadasByAccountAndDateBetween(ab.getId(), start, end);
        return buys.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<BuyP2PDto> getTodayNoAsignadasAllAccounts() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        List<BuyP2PDto> out = new ArrayList<>();

        List<AccountBinance> binanceAccounts = accountBinanceRepository.findAll().stream()
                .filter(acc -> "BINANCE".equalsIgnoreCase(acc.getTipo()))
                .collect(Collectors.toList());

        for (AccountBinance acc : binanceAccounts) {
            createBuyP2pDirectly(acc.getName(), today);

            List<BuyP2P> buys = buyP2PRepository.findNoAsignadasByAccountAndDateBetween(acc.getId(), start, end);
            out.addAll(buys.stream().map(this::toDto).collect(Collectors.toList()));
        }

        return out;
    }

    // =========================
    // 3) ASIGNACIÓN CUENTAS COP
    // =========================
    @Override
    public String processAssignAccountCop(Integer buyId, List<AssignAccountDto> accounts) {
        BuyP2P buy = buyP2PRepository.findById(buyId).orElse(null);
        if (buy == null) return "No se encontró la compra con id " + buyId;
        if (Boolean.TRUE.equals(buy.getAsignado())) return "Compra ya fue asignada";

        // prepara lista
        if (buy.getAccountCopsDetails() == null) buy.setAccountCopsDetails(new ArrayList<>());
        else {
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
                AccountCop acc = accountCopService.findByIdAccountCop(dto.getAccountCop());

                // ✅ COMPRA = SALIDA COP => RESTA saldo
                double current = acc.getBalance() != null ? acc.getBalance() : 0.0;
                double monto = dto.getAmount() != null ? dto.getAmount() : 0.0;
                acc.setBalance(current - monto);
                accountCopService.saveAccountCop(acc);

                det.setAccountCop(acc);
            } else {
                det.setAccountCop(null); // externa
            }

            total += dto.getAmount() != null ? dto.getAmount() : 0.0;
            buy.getAccountCopsDetails().add(det);
        }

        // opcional: validación
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