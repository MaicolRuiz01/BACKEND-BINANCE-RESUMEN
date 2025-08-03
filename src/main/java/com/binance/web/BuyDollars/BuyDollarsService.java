package com.binance.web.BuyDollars;

import java.util.List;

import com.binance.web.Entity.BuyDollars;

public interface BuyDollarsService {

	BuyDollars createBuyDollars(BuyDollarsDto buyDollarsDto);

	BuyDollars getLastBuyDollars();
	
	public List<BuyDollars> getAllBuyDollars();

	BuyDollars updateBuyDollars(Integer id, BuyDollarsDto dto);

}
