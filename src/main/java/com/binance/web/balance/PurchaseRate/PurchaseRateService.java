package com.binance.web.balance.PurchaseRate;

import com.binance.web.Entity.BuyDollars;

public interface PurchaseRateService {
	void addPurchaseRate(BuyDollars lastBuy);
}
