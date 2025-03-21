package com.binance.web.Supplier;

public interface SupplierService {
	void saveSupplier(Supplier supplier);
	void subtractSupplierDebt(Double pesosCop, String taxType);
}
