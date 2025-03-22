package com.binance.web.Supplier;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SupplierServiceImpl implements SupplierService {
	
	@Autowired
	private SupplierRepository supplierRepository;
	
	@Override
	public void saveSupplier(Supplier supplier) {
		supplierRepository.save(supplier);
	}

	@Override
	public void subtractSupplierDebt(Double pesosCop, String taxType) {
		Supplier supplier = supplierRepository.findByName("Deuda");
		if(taxType.contentEquals("2x")) {
			pesosCop = pesosCop * 0.998;
		}
		
		if(taxType.contentEquals("4x")) {
			pesosCop = pesosCop * 0.996;
		}
		
		Double balance = supplier.getBalance() - pesosCop;
		supplier.setBalance(balance);
		saveSupplier(supplier);
	}
	
	public void subtractAllSalesFromSupplier() {
		Double ventasNoAsignadas = 0.0;
		List<Supplier> supplier = supplierRepository.findAll();
	}
}
