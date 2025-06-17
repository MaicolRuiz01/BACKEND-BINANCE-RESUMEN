package com.binance.web.BuyDollars;

import com.binance.web.Entity.BuyDollars;

public interface BuyDollarsService {

	BuyDollars createBuyDollars(BuyDollarsDto buyDollarsDto);

	BuyDollars getLastBuyDollars();

}
