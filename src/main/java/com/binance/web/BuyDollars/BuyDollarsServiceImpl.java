package com.binance.web.BuyDollars;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.BinanceAPI.PaymentController;
import com.binance.web.BinanceAPI.SpotOrdersController;
import com.binance.web.BinanceAPI.TronScanController;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.Spot.SpotController;
import com.binance.web.averageRate.AverageRateService;
import com.binance.web.balance.PurchaseRate.PurchaseRateService;

@Service
public class BuyDollarsServiceImpl implements BuyDollarsService {

	@Autowired
	private BuyDollarsRepository buyDollarsRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private AccountBinanceRepository accountBinanceRepository;
	
	@Autowired
	private AccountBinanceService accountBinanceService;

	@Autowired
	private PurchaseRateService purchaseRateService;
	
	@Autowired
	private AverageRateService averageRateService;
	@Autowired
	private PaymentController binancePayController;
	@Autowired
	private SpotOrdersController spotOrdersController;
	@Autowired
	private TronScanController tronScanController;

	@Override
	@Transactional
	public BuyDollars createBuyDollars(BuyDollarsDto dto) {
		Supplier supplier = supplierRepository.findById(dto.getSupplierId())
				.orElseThrow(() -> new RuntimeException("Supplier with ID " + dto.getSupplierId() + "not found"));

		AccountBinance accountBinance = accountBinanceRepository.findById(dto.getAccountBinanceId())
				.orElseThrow(() -> new RuntimeException("Account not found"));
		
		BuyDollars buy = new BuyDollars();
		buy.setDollars(dto.getDollars());
		buy.setTasa(dto.getTasa());
		buy.setNameAccount(dto.getNameAccount());
		buy.setDate(dto.getDate());
		buy.setSupplier(supplier);
		buy.setAccountBinance(accountBinance);
		buy.setPesos(dto.getPesos());
		buy.setIdDeposit(dto.getIdDeposit());

		// Valores protegidos contra null
		Double dollars = dto.getDollars() != null ? dto.getDollars() : 0.0;
		Double tasa = dto.getTasa() != null ? dto.getTasa() : 0.0;
		Double supplierBalance = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
		Double accountBalance = accountBinance.getBalance() != null ? accountBinance.getBalance() : 0.0;

		double montoSumar = dollars * tasa;
		supplier.setBalance(supplierBalance + montoSumar);
		accountBinance.setBalance(accountBalance + dollars);
		
		
		AverageRate ultimaTasa =  averageRateService.getUltimaTasaPromedio();
		Double saldoTotalInternoAnteriorUSDT = accountBinanceService.getTotalBalanceInterno().doubleValue() - dollars;
		Double pesosAnteriores = saldoTotalInternoAnteriorUSDT * ultimaTasa.getAverageRate();
		
		Double pesosTotal =  pesosAnteriores + montoSumar;
		Double usdtTotal = dollars + saldoTotalInternoAnteriorUSDT;
		Double nuevaTasaPromedio = pesosTotal / usdtTotal;
		Double nuevoSaldo = saldoTotalInternoAnteriorUSDT + dollars;
		
		averageRateService.guardarNuevaTasa(nuevaTasaPromedio, nuevoSaldo);
		
		accountBinanceRepository.save(accountBinance);
		supplierRepository.save(supplier);
		//desactivo esto porque da error
		purchaseRateService.addPurchaseRate(buy);

		return buyDollarsRepository.save(buy);
	}

	@Override
	public BuyDollars getLastBuyDollars() {
		BuyDollars lastBuy = buyDollarsRepository.findTopByOrderByDateDesc();
		if (lastBuy == null) {
			throw new RuntimeException("No hay registros de BuyDollars");
		}
		return lastBuy;
	}
	
	@Override
	public List<BuyDollars> getAllBuyDollars() {
	    return buyDollarsRepository.findAll(Sort.by(Sort.Direction.DESC, "date"));
	}
	
	@Override
	@Transactional
	public BuyDollars updateBuyDollars(Integer id, BuyDollarsDto dto) {
	    BuyDollars existing = buyDollarsRepository.findById(id)
	        .orElseThrow(() -> new RuntimeException("Compra con ID " + id + " no encontrada"));

	    // Monto anterior en pesos y dólares
	    Double oldDollars = existing.getDollars();
	    Double oldTasa = existing.getTasa();
	    double oldAmountPesos = oldDollars * oldTasa;

	    Supplier oldSupplier = existing.getSupplier();
	    AccountBinance oldAccount = existing.getAccountBinance();

	    // Restar montos anteriores
	    oldSupplier.setBalance(oldSupplier.getBalance() - oldAmountPesos);
	    oldAccount.setBalance(oldAccount.getBalance() - oldDollars);

	    // Nuevos valores
	    Double newDollars = dto.getDollars();
	    Double newTasa = dto.getTasa();
	    double newAmountPesos = newDollars * newTasa;

	    // Actualizar campos básicos
	    existing.setDollars(newDollars);
	    existing.setTasa(newTasa);
	    existing.setPesos(dto.getPesos());
	    existing.setDate(dto.getDate());
	    existing.setNameAccount(dto.getNameAccount());
	    existing.setIdDeposit(dto.getIdDeposit());

	    // Cambiar proveedor si es necesario
	    if (!oldSupplier.getId().equals(dto.getSupplierId())) {
	        Supplier newSupplier = supplierRepository.findById(dto.getSupplierId())
	            .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
	        existing.setSupplier(newSupplier);
	        newSupplier.setBalance(newSupplier.getBalance() + newAmountPesos);
	        supplierRepository.save(newSupplier);
	    } else {
	        // Si el proveedor es el mismo, simplemente reasignar con nuevo valor
	        oldSupplier.setBalance(oldSupplier.getBalance() + newAmountPesos);
	        supplierRepository.save(oldSupplier);
	    }

	    // Cambiar cuenta Binance si es necesario
	    if (!oldAccount.getId().equals(dto.getAccountBinanceId())) {
	        AccountBinance newAccount = accountBinanceRepository.findById(dto.getAccountBinanceId())
	            .orElseThrow(() -> new RuntimeException("Cuenta Binance no encontrada"));
	        existing.setAccountBinance(newAccount);
	        newAccount.setBalance(newAccount.getBalance() + newDollars);
	        accountBinanceRepository.save(newAccount);
	    } else {
	        oldAccount.setBalance(oldAccount.getBalance() + newDollars);
	        accountBinanceRepository.save(oldAccount);
	    }

	    return buyDollarsRepository.save(existing);
	}

	@Override
	@Transactional
	public void registrarComprasAutomaticamente() {
	    try {
	        List<BuyDollarsDto> binancePay = binancePayController.getComprasNoRegistradas().getBody();
	        List<BuyDollarsDto> spot = spotOrdersController.getComprasNoRegistradas(30).getBody();
	        List<BuyDollarsDto> trust = tronScanController.getUSDTIncomingTransfers().getBody();

	        Set<String> existentes = buyDollarsRepository.findAll().stream()
	            .map(BuyDollars::getIdDeposit)
	            .collect(Collectors.toSet());

	        List<BuyDollarsDto> todas = new ArrayList<>();
	        if (binancePay != null) todas.addAll(binancePay);
	        if (spot != null) todas.addAll(spot);
	        if (trust != null) todas.addAll(trust);

	        for (BuyDollarsDto dto : todas) {
	            if (dto.getIdDeposit() == null || existentes.contains(dto.getIdDeposit())) continue;

	            AccountBinance account = accountBinanceRepository.findByName(dto.getNameAccount());
	            if (account == null) continue;

	            // Actualizar balance de la cuenta
	            Double actual = account.getBalance() != null ? account.getBalance() : 0.0;
	            account.setBalance(actual + dto.getDollars());
	            accountBinanceRepository.save(account);

	            BuyDollars nueva = new BuyDollars();
	            nueva.setIdDeposit(dto.getIdDeposit());
	            nueva.setNameAccount(dto.getNameAccount());
	            nueva.setDate(dto.getDate());
	            nueva.setDollars(dto.getDollars());
	            nueva.setTasa(0.0);
	            nueva.setPesos(0.0);
	            nueva.setAsignada(false);
	            nueva.setAccountBinance(account);

	            buyDollarsRepository.save(nueva);
	            existentes.add(dto.getIdDeposit());
	        }

	    } catch (Exception e) {
	        // Logueá el error o lanzalo si querés fallar la transacción
	        throw new RuntimeException("Error al registrar compras automáticamente", e);
	    }
	}
	
	@Override
	public List<BuyDollarsDto> getComprasNoAsignadasHoy() {
	    LocalDateTime start = LocalDate.now().atStartOfDay();
	    LocalDateTime end = LocalDate.now().atTime(LocalTime.MAX);
	    List<BuyDollars> compras = buyDollarsRepository.findNoAsignadasHoy(start, end);

	    return compras.stream().map(buy -> {
	        BuyDollarsDto dto = new BuyDollarsDto();
	        dto.setId(buy.getId());
	        dto.setDollars(buy.getDollars());
	        dto.setTasa(buy.getTasa());
	        dto.setNameAccount(buy.getNameAccount());
	        dto.setDate(buy.getDate());
	        dto.setIdDeposit(buy.getIdDeposit());
	        dto.setPesos(buy.getPesos());
	        dto.setAccountBinanceId(buy.getAccountBinance().getId());
	        dto.setAsignada(buy.getAsignada());
	        return dto;
	    }).collect(Collectors.toList());
	}

	@Override
	@Transactional
	public BuyDollars asignarCompra(Integer id, BuyDollarsDto dto) {
	    // Obtener la compra no asignada existente
	    BuyDollars existing = buyDollarsRepository.findById(id)
	        .orElseThrow(() -> new RuntimeException("Compra no encontrada"));

	    if (Boolean.TRUE.equals(existing.getAsignada())) {
	        throw new RuntimeException("Compra ya fue asignada");
	    }

	    // Insertar datos necesarios desde DTO:
	    existing.setTasa(dto.getTasa());
	    existing.setPesos(dto.getDollars() * dto.getTasa());

	    // Asignar proveedor
	    Supplier supplier = supplierRepository.findById(dto.getSupplierId())
	        .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
	    existing.setSupplier(supplier);

	    // Actualizar balances:
	    double montoPesos = dto.getDollars() * dto.getTasa();
	    double currentSupplierBalance = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
	    supplier.setBalance(currentSupplierBalance + montoPesos);
	    supplierRepository.save(supplier);

	    // Marcar como asignada
	    existing.setAsignada(true);

	    return buyDollarsRepository.save(existing);
	}
}
