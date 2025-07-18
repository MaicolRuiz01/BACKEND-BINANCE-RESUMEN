package com.binance.web.Supplier;

import java.time.LocalDate;
import java.util.List;

import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Supplier;

public interface SupplierService {
	List<Supplier> findAllSuppliers();
	void saveSupplier(Supplier supplier);
	void subtractSupplierDebt(Double pesosCop, Integer supplierId, Integer accountCopId);
	Double subtractAllSalesFromSupplier();
	Supplier getSupplierById(int id);
	void addBuyDollars(BuyDollars buyDollars);
	Supplier updateSupplierBalance(Integer id, Supplier supplier);
}
