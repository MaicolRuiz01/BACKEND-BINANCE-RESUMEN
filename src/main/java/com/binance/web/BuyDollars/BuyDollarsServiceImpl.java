package com.binance.web.BuyDollars;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SupplierRepository;
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

	    // Restaurar balances anteriores
	    Double oldAmount = existing.getDollars() * existing.getTasa();
	    Supplier oldSupplier = existing.getSupplier();
	    AccountBinance oldAccount = existing.getAccountBinance();
	    oldSupplier.setBalance(oldSupplier.getBalance() - oldAmount);
	    oldAccount.setBalance(oldAccount.getBalance() - existing.getDollars());

	    // Actualizar campos
	    existing.setDollars(dto.getDollars());
	    existing.setTasa(dto.getTasa());
	    existing.setPesos(dto.getPesos());
	    existing.setDate(dto.getDate());
	    existing.setNameAccount(dto.getNameAccount());
	    existing.setIdDeposit(dto.getIdDeposit());

	    // Nueva cuenta y proveedor si se cambiaron
	    if (!oldSupplier.getId().equals(dto.getSupplierId())) {
	        Supplier newSupplier = supplierRepository.findById(dto.getSupplierId())
	            .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
	        existing.setSupplier(newSupplier);
	        oldSupplier = newSupplier;
	    }

	    if (!oldAccount.getId().equals(dto.getAccountBinanceId())) {
	        AccountBinance newAccount = accountBinanceRepository.findById(dto.getAccountBinanceId())
	            .orElseThrow(() -> new RuntimeException("Cuenta Binance no encontrada"));
	        existing.setAccountBinance(newAccount);
	        oldAccount = newAccount;
	    }

	    // Reasignar balances
	    double nuevoMonto = dto.getDollars() * dto.getTasa();
	    oldSupplier.setBalance(oldSupplier.getBalance() + nuevoMonto);
	    oldAccount.setBalance(oldAccount.getBalance() + dto.getDollars());

	    supplierRepository.save(oldSupplier);
	    accountBinanceRepository.save(oldAccount);

	    return buyDollarsRepository.save(existing);
	}


}
