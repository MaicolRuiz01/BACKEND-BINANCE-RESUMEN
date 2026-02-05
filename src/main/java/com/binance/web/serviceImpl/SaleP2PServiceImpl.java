package com.binance.web.serviceImpl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;

import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SaleP2pAccountCop;
import com.binance.web.OrderP2P.OrderP2PDto;
import com.binance.web.OrderP2P.OrderP2PService;
import com.binance.web.Repository.AccountBinanceRepository;

import com.binance.web.Repository.SaleP2PRepository;
import com.binance.web.Repository.SaleP2pAccountCopRepository;
import com.binance.web.model.AssignAccountDto;
import com.binance.web.model.SaleP2PDto;
import com.binance.web.service.AccountBinanceService;
import com.binance.web.service.AccountCopService;
import com.binance.web.service.SaleP2PService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Service
public class SaleP2PServiceImpl implements SaleP2PService {

    @Autowired
    private SaleP2PRepository saleP2PRepository;

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
	@Autowired
	private SaleP2pAccountCopRepository saleP2pAccountCopRepository;


    
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

    LocalDateTime inicio = today.atStartOfDay();
    LocalDateTime fin = inicio.plusDays(1); // usamos [inicio, fin)

    for (JsonElement element : dataArray) {
        JsonObject obj = element.getAsJsonObject();

        // ✅ filtros duros (por seguridad)
        String status = obj.has("orderStatus") ? obj.get("orderStatus").getAsString() : "";
        String tradeType = obj.has("tradeType") ? obj.get("tradeType").getAsString() : "";
        String asset = obj.has("asset") ? obj.get("asset").getAsString() : "";

        if (!"COMPLETED".equalsIgnoreCase(status)) continue;
        if (!"SELL".equalsIgnoreCase(tradeType)) continue;
        if (!"USDT".equalsIgnoreCase(asset)) continue;

        String orderNumber = obj.get("orderNumber").getAsString();
        LocalDateTime fechaOrden = convertTimestamp(obj.get("createTime").getAsLong());

        if (fechaOrden.isBefore(inicio) || !fechaOrden.isBefore(fin)) continue; // fechaOrden >= fin => fuera
        if (saleP2PRepository.existsByNumberOrder(orderNumber)) continue;

        SaleP2P sale = new SaleP2P();
        sale.setNumberOrder(orderNumber);
        sale.setDate(fechaOrden);

        // ✅ COP real viene como totalPrice
        Double pesosCop = obj.has("totalPrice") && !obj.get("totalPrice").isJsonNull()
                ? obj.get("totalPrice").getAsDouble()
                : 0.0;

        // ✅ USDT vendido viene como amount
        Double dollarsUs = obj.has("amount") && !obj.get("amount").isJsonNull()
                ? obj.get("amount").getAsDouble()
                : 0.0;

        // ✅ comisión real suele venir como takerCommission (en tu JSON commission sale "0")
        Double commission = obj.has("takerCommission") && !obj.get("takerCommission").isJsonNull()
                ? obj.get("takerCommission").getAsDouble()
                : (
                    obj.has("commission") && !obj.get("commission").isJsonNull()
                        ? obj.get("commission").getAsDouble()
                        : 0.0
                );

        sale.setPesosCop(pesosCop);
        sale.setDollarsUs(dollarsUs);
        sale.setCommission(commission);

        // Tasa: pesos / usdt (solo amount)
        sale.setTasa(calculateTasaVenta(sale));

        sale = assignAccountBinance(sale, account);

        // ✅ por defecto no asignada
        sale.setAsignado(false);
        sale.setUtilidad(0.0);

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
    @Transactional
    public String processAssignAccountCop(Integer saleId, List<AssignAccountDto> accounts) {

        SaleP2P sale = saleP2PRepository.findById(saleId).orElse(null);
        if (sale == null) return "No se encontró la venta con id " + saleId;

        if (Boolean.TRUE.equals(sale.getAsignado())) {
            return "Esta venta ya está asignada.";
        }

        // ✅ Asegura lista inicializada una sola vez
        if (sale.getAccountCopsDetails() == null) {
            sale.setAccountCopsDetails(new ArrayList<>());
        }

        // ✅ 1) REVERTIR EFECTOS de detalles anteriores (si existían)
        for (SaleP2pAccountCop prev : sale.getAccountCopsDetails()) {
            if (prev.getAccountCop() != null) {
                AccountCop acc = accountCopService.findByIdAccountCop(prev.getAccountCop().getId());
                if (acc != null) {
                    double amt = prev.getAmount() != null ? prev.getAmount() : 0.0;

                    if (acc.getBalance() == null) acc.setBalance(0.0);
                    if (acc.getCupoDisponibleHoy() == null) acc.setCupoDisponibleHoy(0.0);

                    acc.setBalance(acc.getBalance() - amt);
                    acc.setCupoDisponibleHoy(acc.getCupoDisponibleHoy() + amt);

                    accountCopService.saveAccountCopSafe(acc);
                }
            }
        }

        // ✅ 2) BORRAR detalles anteriores (MISMA lista, NO reasignar)
        sale.getAccountCopsDetails().clear();

        // ✅ 3) APLICAR nuevas asignaciones
        for (AssignAccountDto dto : accounts) {

            SaleP2pAccountCop detail = new SaleP2pAccountCop();
            detail.setSaleP2p(sale);
            detail.setAmount(dto.getAmount());
            detail.setNameAccount(dto.getNameAccount());

            if (dto.getAccountCop() != null) {
                AccountCop acc = accountCopService.findByIdAccountCop(dto.getAccountCop());
                detail.setAccountCop(acc);

                double amt = dto.getAmount() != null ? dto.getAmount() : 0.0;

                if (acc.getBalance() == null) acc.setBalance(0.0);
                if (acc.getCupoDisponibleHoy() == null) acc.setCupoDisponibleHoy(0.0);

                acc.setBalance(acc.getBalance() + amt);
                acc.setCupoDisponibleHoy(acc.getCupoDisponibleHoy() - amt);

                accountCopService.saveAccountCopSafe(acc);
            }

            sale.getAccountCopsDetails().add(detail);
        }

        accountBinanceService.subtractBalance(sale.getBinanceAccount().getName(), sale.getDollarsUs());

        if (sale.getUtilidad() == null) sale.setUtilidad(0.0);
        sale.setUtilidad(sale.getUtilidad() + generateTax(sale));

        sale.setAsignado(true);

        // ✅ con cascade=ALL se guardan detalles
        saleP2PRepository.save(sale);

        return "Asignación realizada con éxito";
    }




    @Transactional
    public SaleP2P assignAccountCop(List<AssignAccountDto> accounts, SaleP2P sale) {

        // 1) REVERSAR asignaciones anteriores (si existían)
        if (sale.getAccountCopsDetails() != null && !sale.getAccountCopsDetails().isEmpty()) {

            for (SaleP2pAccountCop prev : sale.getAccountCopsDetails()) {

                if (prev.getAccountCop() != null) {
                    AccountCop acc = accountCopService.findByIdAccountCop(prev.getAccountCop().getId());
                    if (acc != null) {
                        double amt = prev.getAmount() != null ? prev.getAmount() : 0.0;

                        if (acc.getBalance() == null) acc.setBalance(0.0);
                        if (acc.getCupoDisponibleHoy() == null) acc.setCupoDisponibleHoy(0.0);

                        // ✅ REVERSA EFECTOS (porque estás quitando esa asignación)
                        acc.setBalance(acc.getBalance() - amt);
                        acc.setCupoDisponibleHoy(acc.getCupoDisponibleHoy() + amt);

                        // ✅ NO uses saveAccountCop() (ese resetea cupos)
                        accountCopService.saveAccountCopSafe(acc);
                    }
                }
            }

            // limpiar relación
            for (SaleP2pAccountCop prev : sale.getAccountCopsDetails()) {
                prev.setSaleP2p(null);
            }
            sale.getAccountCopsDetails().clear();

        } else {
            sale.setAccountCopsDetails(new ArrayList<>());
        }

        // 2) APLICAR nuevas asignaciones
        for (AssignAccountDto dto : accounts) {

            SaleP2pAccountCop detail = new SaleP2pAccountCop();
            detail.setSaleP2p(sale);
            detail.setAmount(dto.getAmount());
            detail.setNameAccount(dto.getNameAccount());

            if (dto.getAccountCop() != null) {
                AccountCop acc = accountCopService.findByIdAccountCop(dto.getAccountCop());
                detail.setAccountCop(acc);

                double amt = dto.getAmount() != null ? dto.getAmount() : 0.0;

                if (acc.getBalance() == null) acc.setBalance(0.0);
                if (acc.getCupoDisponibleHoy() == null) acc.setCupoDisponibleHoy(0.0);

                // ✅ APLICA EFECTOS
                acc.setBalance(acc.getBalance() + amt);
                acc.setCupoDisponibleHoy(acc.getCupoDisponibleHoy() - amt);

                accountCopService.saveAccountCopSafe(acc);
            }

            sale.getAccountCopsDetails().add(detail);
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
        if (accountBinance == null) return List.of();

        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end   = start.plusDays(1);

        return saleP2PRepository
                .findByAccountAndDateBetween(accountBinance.getId(), start, end)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }


    private SaleP2PDto convertToDto(SaleP2P sale) {
        SaleP2PDto dto = new SaleP2PDto();
        dto.setId(sale.getId());
        dto.setNumberOrder(sale.getNumberOrder());
        dto.setDate(sale.getDate());
        dto.setCommission(sale.getCommission());
        dto.setPesosCop(sale.getPesosCop());
        dto.setDollarsUs(sale.getDollarsUs());
        dto.setTasa(sale.getTasa()); // ✅ ya no queda null si está guardada
        dto.setNameAccountBinance(getBinanceAccountName(sale));

        // ✅ Si tu DTO tiene campo asignado, actívalo:
        // dto.setAsignado(Boolean.TRUE.equals(sale.getAsignado()));

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
	
	@Override
	public String getAllP2PFromBinance(String account, LocalDate from, LocalDate to) {
	    ZoneId zone = ZoneId.of("America/Bogota");

	    long start = from.atStartOfDay(zone).toInstant().toEpochMilli();
	    long end   = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

	    // ✅ ESTE MÉTODO EXISTE
	    return binanceService.getP2POrdersInRange(account, start, end);
	}
	@Override
	public List<SaleP2PDto> getTodayNoAsignadas(String account) {
	    LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));
	    LocalDateTime start = today.atStartOfDay();
	    LocalDateTime end = start.plusDays(1);

	    // importa de Binance y guarda nuevas
	    createSaleP2pDirectly(account, today);

	    AccountBinance ab = accountBinanceRepository.findByName(account);
	    List<SaleP2P> sales = saleP2PRepository.findNoAsignadasByAccountAndDateBetween(ab.getId(), start, end);
	    return sales.stream().map(this::convertToDto).collect(Collectors.toList());
	}
	
	private void importSalesP2PRange(String account, LocalDate from, LocalDate to) {
	    try {
	        ZoneId zone = ZoneId.of("America/Bogota");
	        long start = from.atStartOfDay(zone).toInstant().toEpochMilli();
	        long end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

	        // ✅ Pide a Binance por rango (por defecto tu método trae SELL fijo o tradeType)
	        String jsonResponse = binanceService.getP2POrdersInRange(account, start, end);

	        JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
	        if (jsonObject.has("error")) {
	            System.err.println("❌ Error Binance (" + account + "): " + jsonObject.get("error").getAsString());
	            return;
	        }

	        JsonArray dataArray = jsonObject.getAsJsonArray("data");
	        if (dataArray == null || dataArray.size() == 0) {
	            return;
	        }

	        for (JsonElement element : dataArray) {
	            JsonObject obj = element.getAsJsonObject();

	            // ✅ filtros duros
	            String status = obj.has("orderStatus") ? obj.get("orderStatus").getAsString() : "";
	            String tradeType = obj.has("tradeType") ? obj.get("tradeType").getAsString() : "";
	            String asset = obj.has("asset") ? obj.get("asset").getAsString() : "";

	            if (!"COMPLETED".equalsIgnoreCase(status)) continue;
	            if (!"SELL".equalsIgnoreCase(tradeType)) continue;
	            if (!"USDT".equalsIgnoreCase(asset)) continue;

	            String orderNumber = obj.has("orderNumber") ? obj.get("orderNumber").getAsString() : null;
	            if (orderNumber == null || orderNumber.isBlank()) continue;

	            // ✅ evita duplicados
	            if (saleP2PRepository.existsByNumberOrder(orderNumber)) continue;

	            // Fecha
	            LocalDateTime fechaOrden = obj.has("createTime")
	                    ? convertTimestamp(obj.get("createTime").getAsLong())
	                    : LocalDateTime.now(zone);

	            // COP real (totalPrice)
	            Double pesosCop = (obj.has("totalPrice") && !obj.get("totalPrice").isJsonNull())
	                    ? obj.get("totalPrice").getAsDouble()
	                    : 0.0;

	            // USDT vendido (amount)
	            Double dollarsUs = (obj.has("amount") && !obj.get("amount").isJsonNull())
	                    ? obj.get("amount").getAsDouble()
	                    : 0.0;

	            // comisión (takerCommission o commission)
	            Double commission = (obj.has("takerCommission") && !obj.get("takerCommission").isJsonNull())
	                    ? obj.get("takerCommission").getAsDouble()
	                    : ((obj.has("commission") && !obj.get("commission").isJsonNull())
	                            ? obj.get("commission").getAsDouble()
	                            : 0.0);

	            SaleP2P sale = new SaleP2P();
	            sale.setNumberOrder(orderNumber);
	            sale.setDate(fechaOrden);
	            sale.setPesosCop(pesosCop);
	            sale.setDollarsUs(dollarsUs);
	            sale.setCommission(commission);

	            // ✅ tasa siempre calculada
	            sale.setTasa(calculateTasaVenta(sale));

	            // ✅ asigna cuenta binance
	            sale = assignAccountBinance(sale, account);

	            // ✅ no asignada por defecto
	            sale.setAsignado(false);
	            sale.setUtilidad(0.0);

	            saveSaleP2P(sale);
	        }

	    } catch (Exception e) {
	        e.printStackTrace();
	        System.err.println("❌ Error importando rango P2P (" + account + "): " + e.getMessage());
	    }
	}


	@Override
	public List<SaleP2PDto> getTodayNoAsignadasAllAccounts() {
	    ZoneId zone = ZoneId.of("America/Bogota");

	    // ✅ SOLO HOY
	    LocalDate today = LocalDate.now(zone);
	    LocalDate from = today;
	    LocalDate to   = today;

	    List<SaleP2PDto> out = new ArrayList<>();

	    List<AccountBinance> binanceAccounts = accountBinanceRepository.findAll().stream()
	            .filter(acc -> "BINANCE".equalsIgnoreCase(acc.getTipo()))
	            .collect(Collectors.toList());

	    for (AccountBinance acc : binanceAccounts) {
	        // 1) Importa SOLO HOY
	        importSalesP2PRange(acc.getName(), from, to);

	        // 2) Devuelve NO asignadas de esa cuenta (sin filtrar por fecha)
	        List<SaleP2P> sales = saleP2PRepository.findNoAsignadasGeneralByAccount(acc.getId());

	        out.addAll(sales.stream().map(this::convertToDto).collect(Collectors.toList()));
	    }

	    return out;
	}

	
	@Override
	@Transactional
	public String fixDuplicateAssignmentsAuto(Integer saleP2pId) {

	    // 1) Traer todos los detalles de esa venta
	    List<SaleP2pAccountCop> details = saleP2pAccountCopRepository.findBySaleP2p_Id(saleP2pId);

	    if (details == null || details.size() <= 1) {
	        return "OK: La venta " + saleP2pId + " no tiene duplicados.";
	    }

	    // 2) Agrupar por "misma cuenta + mismo monto"
	    // clave: si hay accountCop -> ID, si no -> nameAccount
	    Map<String, List<SaleP2pAccountCop>> grouped = new HashMap<>();

	    for (SaleP2pAccountCop d : details) {
	        String accKey = (d.getAccountCop() != null)
	                ? "ID:" + d.getAccountCop().getId()
	                : "NAME:" + (d.getNameAccount() == null ? "" : d.getNameAccount().trim().toUpperCase());

	        double amount = d.getAmount() != null ? d.getAmount() : 0.0;

	        String key = accKey + "|AMT:" + amount;
	        grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
	    }

	    int removed = 0;

	    // 3) Para cada grupo duplicado, dejamos 1 y borramos el resto
	    for (List<SaleP2pAccountCop> list : grouped.values()) {
	        if (list.size() <= 1) continue;

	        // deja el menor id como "bueno" y borra los demás
	        list.sort(Comparator.comparing(SaleP2pAccountCop::getId));

	        for (int i = 1; i < list.size(); i++) {
	            SaleP2pAccountCop dup = list.get(i);

	            // 3.1) Revertir balance/cupo SOLO para ese duplicado
	            if (dup.getAccountCop() != null) {
	                AccountCop acc = accountCopService.findByIdAccountCop(dup.getAccountCop().getId());
	                if (acc != null) {
	                    double amt = dup.getAmount() != null ? dup.getAmount() : 0.0;

	                    if (acc.getBalance() == null) acc.setBalance(0.0);
	                    acc.setBalance(acc.getBalance() - amt);

	                    if (acc.getCupoDisponibleHoy() == null) acc.setCupoDisponibleHoy(0.0);
	                    acc.setCupoDisponibleHoy(acc.getCupoDisponibleHoy() + amt);

	                    accountCopService.saveAccountCopSafe(acc);
	                }
	            }

	            // 3.2) Borrar el duplicado
	            saleP2pAccountCopRepository.deleteById(dup.getId());
	            removed++;
	        }
	    }

	    if (removed == 0) {
	        return "OK: No se detectaron duplicados exactos en la venta " + saleP2pId + ".";
	    }

	    return "✅ Arreglado: se eliminaron " + removed + " asignación(es) duplicada(s) y se revirtió balance/cupo.";
	}
	
}
