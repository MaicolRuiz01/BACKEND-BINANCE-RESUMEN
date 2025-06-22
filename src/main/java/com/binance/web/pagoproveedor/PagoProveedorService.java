package com.binance.web.pagoproveedor;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.PagoProveedor;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.PagoProveedorRepository;
import com.binance.web.Repository.SupplierRepository;

@Service
public class PagoProveedorService {

	private final PagoProveedorRepository pagoProveedorRepository;
	private final AccountCopRepository accountCopRepository;
	private final SupplierRepository supplierRepository;

	public PagoProveedorService(PagoProveedorRepository pagoProveedorRepositor,
            AccountCopRepository accountCopRepository,
            SupplierRepository supplierRepository) {
		this.pagoProveedorRepository = pagoProveedorRepositor;
		this.accountCopRepository = accountCopRepository;
		this.supplierRepository = supplierRepository;
	}
	
	public List<PagoProveedor> findAllPagos(){
		List<PagoProveedor> pagos = pagoProveedorRepository.findAll();
		return pagos;
	}
	
	// Método para registrar un pago
    public PagoProveedor makePayment(Integer accountCopId, Integer supplierId, Double amount) {
        // Buscar la cuenta y proveedor
        AccountCop accountCop = accountCopRepository.findById(accountCopId).orElse(null);
        Supplier supplier = supplierRepository.findById(supplierId).orElse(null);

        if (accountCop == null || supplier == null) {
            throw new IllegalArgumentException("Cuenta o proveedor no encontrado");
        }

        if (accountCop.getBalance() < amount) {
            throw new IllegalArgumentException("Saldo insuficiente en la cuenta");
        }

        // Realizar el pago: disminuir el saldo de AccountCop y registrar el pago
        accountCop.setBalance(accountCop.getBalance() - amount);
        supplier.setBalance(supplier.getBalance() + amount);

        // Guardar las actualizaciones
        accountCopRepository.save(accountCop);
        supplierRepository.save(supplier);

        // Crear y guardar el pago
        PagoProveedor payment = new PagoProveedor(amount, LocalDateTime.now(), accountCop, supplier);
        return pagoProveedorRepository.save(payment);
    }
    
    public List<PagoProveedor> getPagosBySupplierId(Integer supplierId) {
        return pagoProveedorRepository.findBySupplierId(supplierId);
    }


}
