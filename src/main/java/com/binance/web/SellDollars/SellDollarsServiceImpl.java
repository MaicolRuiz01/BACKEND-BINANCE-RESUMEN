package com.binance.web.SellDollars;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.binance.web.SaleP2P.AssignAccountDto;
import com.binance.web.model.Transaction;
import com.binance.web.AccountCop.AccountCopService;
import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.BinanceAPI.PaymentController;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.PurchaseRate;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.SellDollarsAccountCop;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.PurchaseRateRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.Entity.Supplier;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SellDollarsServiceImpl implements SellDollarsService{
	
	@Autowired
    private SellDollarsRepository sellDollarsRepository;
	
	@Autowired
    private AccountBinanceRepository accountBinanceRepository;
	@Autowired
	private SupplierRepository supplierRepository;
	@Autowired
	private BinanceService binanceService;
	@Autowired
	private AccountCopService accountCopService; // para obtener y actualizar balances
	@Autowired
	private PaymentController paymentController;
	@Autowired
	private PurchaseRateRepository purchaseRateRepository;

	@Override
	@Transactional
	public SellDollars createSellDollars(SellDollarsDto dto) {
	    AccountBinance accountBinance = accountBinanceRepository.findById(dto.getAccountBinanceId())
	            .orElseThrow(() -> new RuntimeException("AccountBinance not found"));

	    Supplier supplier = supplierRepository.findById(dto.getSupplier())
	            .orElseThrow(() -> new RuntimeException("Supplier no encontrado"));

	    SellDollars sale = new SellDollars();
	    sale.setIdWithdrawals(dto.getIdWithdrawals());
	    sale.setTasa(dto.getTasa());
	    sale.setDollars(dto.getDollars());
	    sale.setDate(dto.getDate());
	    sale.setNameAccount(dto.getNameAccount());
	    sale.setAccountBinance(accountBinance);
	    sale.setPesos(dto.getPesos());
	    sale.setSupplier(supplier);
	    sale.setUtilidad(utilidad(sale));
	    sale.setAsignado(true);

	    // Restar dólares y pesos
	    accountBinance.setBalance((accountBinance.getBalance() != null ? accountBinance.getBalance() : 0.0) - dto.getDollars());
	    supplier.setBalance((supplier.getBalance() != null ? supplier.getBalance() : 0.0) - dto.getPesos());
	    
	    // Manejo de cuentas COP asignadas
	    List<SellDollarsAccountCop> detalles = new ArrayList<>();
	    if (dto.getAccounts() != null) {
	        for (AssignAccountDto assignDto : dto.getAccounts()) {
	            AccountCop acc = accountCopService.findByIdAccountCop(assignDto.getAccountCop());
	            acc.setBalance((acc.getBalance() != null ? acc.getBalance() : 0.0) + assignDto.getAmount());
	            accountCopService.saveAccountCop(acc);

	            SellDollarsAccountCop detalle = new SellDollarsAccountCop();
	            detalle.setSellDollars(sale);
	            detalle.setAccountCop(acc);
	            detalle.setAmount(assignDto.getAmount());
	            detalle.setNameAccount(assignDto.getNameAccount());

	            detalles.add(detalle);
	        }
	    }

	    sale.setSellDollarsAccounts(detalles); // ← Relación en SellDollars

	    accountBinanceRepository.save(accountBinance);
	    supplierRepository.save(supplier);

	    return sellDollarsRepository.save(sale);
	}
	
	@Override
	public List<SellDollars> obtenerVentasEntreFechas(LocalDateTime inicio, LocalDateTime fin){
		return sellDollarsRepository.findByDateBetween(inicio, fin);
	}
	
	
	
	@Override
	@Transactional
	public List<SellDollars> registrarYObtenerVentasNoAsignadas() {
	    Map<String, List<Transaction>> nuevasTransacciones = getTodayOutgoingUnregisteredTransactions();
	    saveNewSellDollars(nuevasTransacciones);
	    return sellDollarsRepository.findByAsignadoFalse();
	}

	
	
	// 1. Trae y filtra las transacciones válidas de Binance Pay
	private Map<String, List<Transaction>> getTodayOutgoingUnregisteredTransactions() {
	    Set<String> idsRegistrados = sellDollarsRepository.findAll()
	        .stream()
	        .map(SellDollars::getIdWithdrawals)
	        .collect(Collectors.toSet());

	    Set<String> binanceUsersRegistrados = accountBinanceRepository.findAll()
	        .stream()
	        .map(AccountBinance::getUserBinance)
	        .filter(Objects::nonNull)
	        .collect(Collectors.toSet());

	    Map<String, List<Transaction>> transaccionesValidasPorCuenta = new HashMap<>();

	    LocalDate hoy = LocalDate.now();

	    for (String cuenta : binanceService.getAllAccountNames()) {
	        String json = binanceService.getPaymentHistory(cuenta);
	        List<Transaction> transacciones = paymentController.parseTransactions(json);

	        List<Transaction> filtradas = transacciones.stream()
	        	    .filter(tx -> tx.getTransactionTime().toLocalDate().isEqual(hoy)) // ✅ Solo transacciones de hoy
	        	    .filter(tx -> tx.getAmount() < 0)
	        	    .filter(tx -> !idsRegistrados.contains(tx.getOrderId()))
	        	    .filter(tx -> tx.getReceiverInfo() == null || 
	        	                  !binanceUsersRegistrados.contains(tx.getReceiverInfo().getName()))
	        	    .collect(Collectors.toList());

	        if (!filtradas.isEmpty()) {
	            transaccionesValidasPorCuenta.put(cuenta, filtradas);
	        }
	    }

	    return transaccionesValidasPorCuenta;
	}


	
	// 2. Convierte y guarda transacciones como entidades SellDollars
	private List<SellDollars> saveNewSellDollars(Map<String, List<Transaction>> transaccionesPorCuenta) {
	    List<SellDollars> nuevasVentas = new ArrayList<>();

	    for (Map.Entry<String, List<Transaction>> entry : transaccionesPorCuenta.entrySet()) {
	        String nameAccount = entry.getKey();
	        List<Transaction> transacciones = entry.getValue();

	        for (Transaction tx : transacciones) {
	            SellDollars venta = new SellDollars();
	            venta.setIdWithdrawals(tx.getOrderId());
	            venta.setDate(tx.getTransactionTime());
	            venta.setDollars(Math.abs(tx.getAmount()));
	            venta.setTasa(0.0);
	            venta.setPesos(0.0);
	            venta.setNameAccount(nameAccount); // ✅ Aquí va el nombre correcto de la cuenta Binance
	            venta.setAsignado(false);

	            nuevasVentas.add(venta);
	        }
	    }

	    return sellDollarsRepository.saveAll(nuevasVentas);
	}
	
	private Double utilidad(SellDollars sell) {
		
		PurchaseRate lastRate = purchaseRateRepository.findTopByOrderByDateDesc();
		Double pesos = sell.getPesos();
		Double usdt = sell.getDollars();
		Double utilidadVenta = pesos - (usdt * lastRate.getRate() ) - (sell.getPesos() * 0.004);  
		
		return utilidadVenta;
	}

	
	//aqui terminara para obtener ventas de binance pay
	
	//metodo para obtener la utilidad de una rango de sell
	public void saveUtilityDefinitive(List<SellDollars> rangoSell, Double averageRate) {
		Double pesosUsdtVendidos = 0.0;
		Double usdtVendidos = 0.0;
		Double utilidad = 0.0;
		for (SellDollars sell: rangoSell) {
			pesosUsdtVendidos= sell.getPesos();
			usdtVendidos= sell.getDollars();
			utilidad = pesosUsdtVendidos - (usdtVendidos * averageRate);
			utilidad = utilidad - generateTax(sell);
			sell.setUtilidad(utilidad);
			sellDollarsRepository.save(sell);
		}
		
	}
	
	//metodo de apoyo para generar los impuestos de las cuentas colombianas
	public Double generateTax(SellDollars sell) {
		Double impuesto= 0.0;
		List<SellDollarsAccountCop> sellAccount = sell.getSellDollarsAccounts();
		for(SellDollarsAccountCop account: sellAccount) {
			if(account.getAccountCop()==null) {
				impuesto = account.getAmount()*0.004;
			} 
		}
		return impuesto;
	}
	


}
