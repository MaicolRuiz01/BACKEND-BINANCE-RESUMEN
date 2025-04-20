package com.binance.web.Supplier;

import java.util.Date;

import com.binance.web.BuyDollars.BuyDollars;

public interface SupplierService {
	void saveSupplier(Supplier supplier);
	void subtractSupplierDebt(Double pesosCop, String taxType, Date date);
	Double subtractAllSalesFromSupplier();
	Supplier getSupplierById(int id);
	void addBuyDollars(BuyDollars buyDollars);
	Supplier updateSupplierBalance(Integer id, Supplier supplier);
}
