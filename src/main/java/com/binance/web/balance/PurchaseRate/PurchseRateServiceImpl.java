package com.binance.web.balance.PurchaseRate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.Balance;
import com.binance.web.Entity.PurchaseRate;
import com.binance.web.Repository.PurchaseRateRepository;

@Service
public class PurchseRateServiceImpl implements PurchaseRateService {

	@Autowired
	private PurchaseRateRepository purchaseRateRepository;

	@Override
	public void createPurchaseRate(Balance balance) {
		Double purchaseRate = balance.getPesos() / balance.getUsdt();
		PurchaseRate tasaCompra = new PurchaseRate();
		tasaCompra.setDate(balance.getDate());
		tasaCompra.setDolares(balance.getUsdt());
		tasaCompra.setPesos(balance.getPesos());
		tasaCompra.setRate(purchaseRate);
		purchaseRateRepository.save(tasaCompra);
	}
}
