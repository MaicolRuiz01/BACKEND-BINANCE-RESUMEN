package com.binance.web.BuyDollars;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.Supplier.Supplier;
import com.binance.web.Supplier.SupplierService;

@Service
public class BuyDollarsServiceImpl implements BuyDollarsService {

	@Autowired
	private BuyDollarsRepository buyDollarsRepository;
	
	@Autowired
	private SupplierService supplierService;
	
	@Autowired
	private AccountBinanceService accountBinanceService;

	@Override
	public void saveBuyDollars(BuyDollars buyDollars) {
		buyDollarsRepository.save(buyDollars);
	}

	public void makeDollarsPurshase(BuyDollarsDto buyDollarsDto) {
		BuyDollars buyDollars = convertDtoToBuyDollars(buyDollarsDto);
		buyDollars = addSupplier(buyDollars, buyDollarsDto.getSupplierId());
		buyDollars = addAccountBinance(buyDollars, buyDollarsDto.getAccountBinanceId());
		saveBuyDollars(buyDollars);
		accountBinanceService.depositDollars(buyDollars.getDollars(), buyDollars.getAccountBinance());
	}
	
	private BuyDollars convertDtoToBuyDollars(BuyDollarsDto buyDollarsDto) {
		BuyDollars buyDollars = new BuyDollars();
		buyDollars.setTasa(buyDollarsDto.getTasa());
		buyDollars.setDollars(buyDollarsDto.getDollars());
		buyDollars.setDate(buyDollarsDto.getDate());
		return buyDollars;
	}
	
	private BuyDollars addSupplier(BuyDollars buyDollars, Integer supplierId) {
		Supplier supplier = supplierService.getSupplierById(supplierId);
		buyDollars.setSupplier(supplier);
		return buyDollars;
	}
	
	private BuyDollars addAccountBinance(BuyDollars buyDollars, Integer accountId) {
		
		return buyDollars;
	}
}
