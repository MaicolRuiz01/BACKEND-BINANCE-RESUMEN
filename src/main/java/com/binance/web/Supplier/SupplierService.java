package com.binance.web.Supplier;

import java.util.Date;

public interface SupplierService {
	Supplier getSupplierById(Integer supplierId);
	void saveSupplier(Supplier supplier);
	void subtractSupplierDebt(Double pesosCop, String taxType, Date date);
	Double subtractAllSalesFromSupplier();
}
