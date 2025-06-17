package com.binance.web.Supplier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Supplier;
import com.binance.web.OrderP2P.OrderP2PDto;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SupplierRepository;

@Service
public class SupplierServiceImpl implements SupplierService {

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private AccountCopRepository accountCopRepository;

	@Autowired
	private BuyDollarsRepository buyDollarsRepository;

	@Override
	public void saveSupplier(Supplier supplier) {
		supplierRepository.save(supplier);
	}

	@Override
	public void subtractSupplierDebt(Double pesosCop, Integer supplierId, Integer accountCopId) {
		Supplier supplier = supplierRepository.findById(supplierId).orElse(null);
		Double balance = supplier.getBalance() - pesosCop;
		supplier.setBalance(balance);
		supplier.setLastPaymentDate(getTodayDate());
		saveSupplier(supplier);
		
		AccountCop accountCop = accountCopRepository.findById(accountCopId).orElse(null);
		accountCop.setBalance(accountCop.getBalance() - pesosCop);
		accountCopRepository.save(accountCop);
	}

	@Override
	public Double subtractAllSalesFromSupplier() {
		return 0.0;
	}

	private List<OrderP2PDto> deleteOrdersWithAssignedAccount(List<OrderP2PDto> ordenes) {
		return ordenes.stream().filter(orden -> orden.getNameAccount() == null).collect(Collectors.toList());
	}

	private LocalDateTime getTodayDate() {
		return LocalDateTime.now();
	}

	@Override
	public Supplier getSupplierById(int id) {
		return supplierRepository.findById(id).orElse(null);
	}

	@Override
	public void addBuyDollars(BuyDollars buyDollars) {
		// Se crea un nuevo BuyDollars
		buyDollarsRepository.save(buyDollars);

		// Actualizamos el balance del Supplier (aquí asumimos que el Supplier con id 1
		// es el único)
		Supplier supplier = supplierRepository.findById(1)
				.orElseThrow(() -> new RuntimeException("Supplier not found"));
		supplier.setBalance(supplier.getBalance() + buyDollars.getDollars());
		supplierRepository.save(supplier);
	}

	@Override
	public Supplier updateSupplierBalance(Integer id, Supplier supplier) {
		// Lógica para actualizar el balance del Supplier
		Supplier existingSupplier = supplierRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Supplier not found"));
		existingSupplier.setBalance(supplier.getBalance());
		existingSupplier.setLastPaymentDate(supplier.getLastPaymentDate());
		return supplierRepository.save(existingSupplier);
	}
}
