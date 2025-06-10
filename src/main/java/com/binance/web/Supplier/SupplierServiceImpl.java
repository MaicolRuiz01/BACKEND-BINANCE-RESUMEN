package com.binance.web.Supplier;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Supplier;
import com.binance.web.OrderP2P.OrderP2PDto;
import com.binance.web.OrderP2P.OrderP2PService;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SupplierRepository;

@Service
public class SupplierServiceImpl implements SupplierService {

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private AccountBinanceRepository accountBinanceRepository;

	@Autowired
	private OrderP2PService orderP2PService;

	@Autowired
	private BuyDollarsRepository buyDollarsRepository;

	@Override
	public void saveSupplier(Supplier supplier) {
		supplierRepository.save(supplier);
	}

	@Override
	public void subtractSupplierDebt(Double pesosCop, String taxType, LocalDate date) {
		Supplier supplier = supplierRepository.findByName("Deuda");

		if (taxType.contentEquals("4x")) {
			pesosCop = pesosCop * 0.996;
		}

		Double balance = supplier.getBalance() - pesosCop;
		supplier.setBalance(balance);
		supplier.setLastPaymentDate(date.atStartOfDay());
		saveSupplier(supplier);
	}

	@Override
	public Double subtractAllSalesFromSupplier() {
		Supplier supplier = supplierRepository.findByName("Deuda");
		Double ventasNoAsignadas = 0.0;
		LocalDate endDate = getTodayDate();
		LocalDate startDate = LocalDate.from(supplier.getLastPaymentDate().toInstant(null)
				.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
		List<AccountBinance> binanceAccout = accountBinanceRepository.findAll();
		for (AccountBinance binAccount : binanceAccout) {
			List<OrderP2PDto> ordenes = orderP2PService.showOrderP2PByDateRange(binAccount.getName(), startDate,
					endDate);
			ordenes = deleteOrdersWithAssignedAccount(ordenes);
			for (OrderP2PDto orden : ordenes) {
				ventasNoAsignadas += orden.getTotalPrice() * 0.996;
			}
		}
		return supplier.getBalance() - ventasNoAsignadas;
	}

	private List<OrderP2PDto> deleteOrdersWithAssignedAccount(List<OrderP2PDto> ordenes) {
		return ordenes.stream().filter(orden -> orden.getNameAccount() == null).collect(Collectors.toList());
	}

	private LocalDate getTodayDate() {
		return LocalDate.now();
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
