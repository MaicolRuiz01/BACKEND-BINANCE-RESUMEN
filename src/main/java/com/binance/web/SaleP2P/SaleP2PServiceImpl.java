package com.binance.web.SaleP2P;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.AccountCop.AccountCopService;
import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.PurchaseRate;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SaleP2pAccountCop;
import com.binance.web.OrderP2P.OrderP2PDto;
import com.binance.web.OrderP2P.OrderP2PService;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.PurchaseRateRepository;
import com.binance.web.Repository.SaleP2PRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
@Service
public class SaleP2PServiceImpl implements SaleP2PService {

    @Autowired
    private SaleP2PRepository saleP2PRepository;

    @Autowired
    private PurchaseRateRepository purchaseRateRepository;

    @Autowired
    private AccountCopService accountCopService;

    @Autowired
    private BinanceService binanceService;

    @Autowired
	private OrderP2PService orderP2PService;

    @Autowired
    private AccountBinanceRepository accountBinanceRepository;

	@Autowired
	private AccountBinanceService accountBinanceService;


    
	@Override
	public List<SaleP2PDto> findAllSaleP2P() {
		return saleP2PRepository.findAll()
				.stream()
				.map(this::convertToDto)
				.collect(Collectors.toList());
}

    @Override
    public SaleP2P findByIdSaleP2P(Integer id) {
        return saleP2PRepository.findById(id).orElse(null);
    }

    @Override
    public void saveSaleP2P(SaleP2P saleP2P) {
        saleP2PRepository.save(saleP2P);
    }

    @Override
    public void updateSaleP2P(Integer id, SaleP2P sale) {
        SaleP2P saleP2P = saleP2PRepository.findById(id).orElse(null);
        saleP2PRepository.save(sale);
    }

    @Override
    public void deleteSaleP2P(Integer id) {
        saleP2PRepository.deleteById(id);
    }

    @Override
    public List<SaleP2PDto> getLastSaleP2pToday(String account) {
        LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));
        createSaleP2pDirectly(account, today);
        return convertToDtoList(today, account);
    }

    private void createSaleP2pDirectly(String account, LocalDate today) {
        String jsonResponse = binanceService.getP2POrderLatest(account);
        JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

        if (jsonObject.has("error")) {
            System.err.println("Error Binance: " + jsonObject.get("error").getAsString());
            return;
        }

        JsonArray dataArray = jsonObject.getAsJsonArray("data");
        List<String> existingOrders = saleP2PRepository.findByDateWithoutTime(today).stream()
                .map(SaleP2P::getNumberOrder)
                .collect(Collectors.toList());

        for (JsonElement element : dataArray) {
            JsonObject obj = element.getAsJsonObject();
            String orderNumber = obj.get("orderNumber").getAsString();
            String status = obj.get("orderStatus").getAsString();

            if (!"COMPLETED".equalsIgnoreCase(status)) continue;
            if (existingOrders.contains(orderNumber)) continue;

            SaleP2P sale = new SaleP2P();
            sale.setNumberOrder(orderNumber);
            sale.setDate(convertTimestamp(obj.get("createTime").getAsLong()));

            Double pesosCop = obj.has("fiatAmount") && !obj.get("fiatAmount").isJsonNull()
                    ? obj.get("fiatAmount").getAsDouble()
                    : 0.0;

            Double dollarsUs = obj.has("amount") && !obj.get("amount").isJsonNull()
                    ? obj.get("amount").getAsDouble()
                    : 0.0;

            Double commission = obj.has("commission") && !obj.get("commission").isJsonNull()
                    ? obj.get("commission").getAsDouble()
                    : 0.0;

            // PRIMERO asigna esos valores al objeto antes de calcular tasas o utilidades
            sale.setPesosCop(pesosCop);
            sale.setDollarsUs(dollarsUs);
            sale.setCommission(commission);

            // AHORA SÍ puedes calcular la tasa y utilidad sin que lance error
            sale.setTasa(calculateTasaVenta(sale));
            sale = assignAccountBinance(sale, account);
            sale.setUtilidad(generateUtilidad(sale));

            saveSaleP2P(sale);

        }
    }

    private LocalDateTime convertTimestamp(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.of("America/Bogota"))
                .toLocalDateTime();
    }

    private Double calculateTasaVenta(SaleP2P sale) {
        if (sale.getDollarsUs() == null || sale.getDollarsUs() == 0.0) return 0.0;
        return sale.getPesosCop() / sale.getDollarsUs();
    }

    private Double generateUtilidad(SaleP2P sale) {
        PurchaseRate lastRate = purchaseRateRepository.findTopByOrderByDateDesc();
        Double pesosUsdtVendidos = sale.getPesosCop();
        Double usdtVendidos = sale.getDollarsUs() + sale.getCommission();
        Double utilidad = pesosUsdtVendidos - (usdtVendidos * lastRate.getRate()) - (sale.getPesosCop() * 0.004);
        return utilidad;
    }

    private Double generateTax(SaleP2P sale) {
        Double impuesto = 0.0;
        List<SaleP2pAccountCop> accountCop = sale.getAccountCopsDetails();
        for (SaleP2pAccountCop account : accountCop) {
            if (account.getAccountCop() == null) {
                impuesto += account.getAmount() * 0.004;
            }
        }
        return impuesto;
    }

    public void saveUtilitydefinitive(List<SaleP2P> rangeSales, Double averageRate) {
        for (SaleP2P sale : rangeSales) {
            Double pesosUsdtVendidos = sale.getPesosCop();
            Double usdtVendidos = sale.getDollarsUs() + sale.getCommission();
            Double utilidad = pesosUsdtVendidos - (usdtVendidos * averageRate);
            utilidad = utilidad - generateTax(sale);
            sale.setUtilidad(utilidad);
            saleP2PRepository.save(sale);
        }
    }

    @Override
    public String processAssignAccountCop(Integer saleId, List<AssignAccountDto> accounts) {
        SaleP2P sale = saleP2PRepository.findById(saleId).orElse(null);
        if (sale == null)
            return "No se realizo la asignacion de cuenta, No se encontro la venta con id " + saleId;
        if (!accounts.isEmpty()) {
            sale = assignAccountCop(accounts, sale);
        }
        accountBinanceService.subtractBalance(sale.getBinanceAccount().getName(), sale.getDollarsUs());
        sale.setUtilidad(sale.getUtilidad() + generateTax(sale));
        saleP2PRepository.save(sale);
        return "Asignacion de cuenta realizada con exito";
    }

    private SaleP2P assignAccountCop(List<AssignAccountDto> accounts, SaleP2P sale) {
        if (sale.getAccountCopsDetails() == null) {
            sale.setAccountCopsDetails(new ArrayList<>());
        } else {
            for (SaleP2pAccountCop acc : sale.getAccountCopsDetails()) {
                acc.setSaleP2p(null);
            }
            sale.getAccountCopsDetails().clear();
        }

        for (AssignAccountDto account : accounts) {
            SaleP2pAccountCop assignAccount = new SaleP2pAccountCop();
            assignAccount.setSaleP2p(sale);
            assignAccount.setAmount(account.getAmount());
            assignAccount.setNameAccount(account.getNameAccount());

            if (account.getAccountCop() != null) {
                AccountCop accountCop = accountCopService.findByIdAccountCop(account.getAccountCop());
                assignAccount.setAccountCop(accountCop);
                accountCop.setBalance(accountCop.getBalance() + account.getAmount());
                accountCopService.saveAccountCop(accountCop);
            }

            sale.getAccountCopsDetails().add(assignAccount);
        }
        return sale;
    }

    private SaleP2P assignAccountBinance(SaleP2P sale, String name) {
        AccountBinance accountBinance = accountBinanceRepository.findByName(name);
        sale.setBinanceAccount(accountBinance);
        return sale;
    }

    private List<SaleP2PDto> convertToDtoList(LocalDate today, String name) {
        AccountBinance accountBinance = accountBinanceRepository.findByName(name);
        List<SaleP2P> sales = saleP2PRepository.findByDateAndBinanceAccount(today, accountBinance.getId());
        return sales.stream().map(this::convertToDto).collect(Collectors.toList());
    }

	private SaleP2PDto convertToDto(SaleP2P sale) {
    SaleP2PDto dto = new SaleP2PDto();
    dto.setId(sale.getId());
    dto.setNumberOrder(sale.getNumberOrder());
    dto.setDate(sale.getDate());
    dto.setCommission(sale.getCommission());
    dto.setPesosCop(sale.getPesosCop());
    dto.setDollarsUs(sale.getDollarsUs());
    dto.setNameAccountBinance(getBinanceAccountName(sale));
    return dto;
}
	
	private String getBinanceAccountName(SaleP2P sale) {
		return sale.getBinanceAccount() != null ? sale.getBinanceAccount().getName() : null;
	}
	
	@Override
	public List<SaleP2P> obtenerVentasPorFecha(LocalDate fecha) {
	    LocalDateTime start = fecha.atStartOfDay();
	    LocalDateTime end = start.plusDays(1);
	    return saleP2PRepository.findByDateBetween(start, end);
	}
	
	@Override
	public Double obtenerComisionesPorFecha(LocalDate fecha) {
	    LocalDateTime start = fecha.atStartOfDay();
	    LocalDateTime end = start.plusDays(1);
	    return saleP2PRepository.findByDateBetween(start, end).stream()
	        .mapToDouble(SaleP2P::getCommission)
	        .sum();
	}

	@Override
	public List<SaleP2P> obtenerVentasEntreFechas(LocalDateTime inicio, LocalDateTime fin) {
		// TODO Auto-generated method stub
		return null;
	}
}
