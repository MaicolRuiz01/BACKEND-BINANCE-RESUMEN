package com.binance.web.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/supplier")
public class SupplierController {
	
	@Autowired
	private SupplierService supplierService;

	 @GetMapping
	    public ResponseEntity<Double> getAllSales() {
	        Double deuda = supplierService.subtractAllSalesFromSupplier();
	        return ResponseEntity.ok(deuda);
	    }
}
