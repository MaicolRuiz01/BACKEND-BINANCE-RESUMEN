package com.binance.web.balance.PurchaseRate;

import java.util.Date;

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
	public void createPurchaseRate(Double PurchaseRate, Balance previousBalance, Date date) {
		PurchaseRate tasaCompra = new PurchaseRate();
		tasaCompra.setDate(date);
		tasaCompra.setDolares(previousBalance.getUsdt());
		tasaCompra.setPesos(previousBalance.getPesos());
		tasaCompra.setRate(PurchaseRate);
		purchaseRateRepository.save(tasaCompra);
	}
}
