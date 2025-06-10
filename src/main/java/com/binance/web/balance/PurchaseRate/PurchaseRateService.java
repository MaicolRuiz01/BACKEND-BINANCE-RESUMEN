package com.binance.web.balance.PurchaseRate;

import com.binance.web.Entity.Balance;
import com.binance.web.Entity.BuyDollars;

public interface PurchaseRateService {
	void createPurchaseRate(Balance balance);

	void addPurchaseRate(BuyDollars lastBuy);
}
