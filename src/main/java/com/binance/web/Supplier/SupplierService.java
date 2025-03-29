package com.binance.web.Supplier;

import java.util.Date;

public interface SupplierService {
	void saveSupplier(Supplier supplier);
	void subtractSupplierDebt(Double pesosCop, String taxType, Date date);
	Double subtractAllSalesFromSupplier();
}
