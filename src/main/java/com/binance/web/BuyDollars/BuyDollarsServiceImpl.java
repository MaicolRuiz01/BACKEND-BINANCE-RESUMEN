package com.binance.web.BuyDollars;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SupplierRepository;
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
	private PurchaseRateService purchaseRateService;

	@Override
	@Transactional
	public BuyDollars createBuyDollars(BuyDollarsDto dto) {
		Supplier supplier = supplierRepository.findById(1)
				.orElseThrow(() -> new RuntimeException("Supplier with ID 1 not found"));

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

		accountBinanceRepository.save(accountBinance);
		supplierRepository.save(supplier);
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
}
