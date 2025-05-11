package com.binance.web.balance.PurchaseRate;

import java.util.Date;

import com.binance.web.Entity.Balance;

public interface PurchaseRateService {
	public void createPurchaseRate(Double PurchaseRate, Balance previousBalance, Date date);
}
