package com.binance.web.Supplier;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.BuyDollars.BuyDollarsService;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Supplier;

@RestController
@RequestMapping("/supplier")
public class SupplierController {

	@Autowired
	private SupplierService supplierService;
	
	@Autowired private BuyDollarsService buyDollarsService;

	@GetMapping(produces = "application/json")
	public ResponseEntity<List<Supplier>> getAllSuppliers() {
		List<Supplier> Suppliers = supplierService.findAllSuppliers();
		return ResponseEntity.ok(Suppliers);
	}

	@PostMapping("/suppliers")
	public ResponseEntity<Void> createSupplier(@RequestBody Supplier supplier) {
    supplierService.saveSupplier(supplier);
    return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@GetMapping("/{id}")
	public ResponseEntity<Supplier> getSupplierById(@PathVariable Integer id) {
		Supplier supplier = supplierService.getSupplierById(id);
		return supplier != null ? ResponseEntity.ok(supplier) : ResponseEntity.notFound().build();
	}

	// Método para agregar dólares a un Supplier
	@PostMapping("/buy-dollars")
	public ResponseEntity<Void> addBuyDollars(@RequestBody BuyDollars buyDollars) {
		supplierService.addBuyDollars(buyDollars); // Método que maneja la lógica para actualizar el balance
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	// Método para actualizar el balance del Supplier
	@PutMapping("/{id}")
	public ResponseEntity<Supplier> updateSupplierBalance(@PathVariable Integer id, @RequestBody Supplier supplier) {
		Supplier updatedSupplier = supplierService.updateSupplierBalance(id, supplier);
		return ResponseEntity.ok(updatedSupplier);
	}

	@PostMapping("/subtract-debt")
	public ResponseEntity<Void> subtractSupplierDebt(@RequestBody SubtractSupplierDebtDto dto) {
		supplierService.subtractSupplierDebt(dto.getPesosCop(), dto.getSupplierId(), dto.getAccountCopId());
		return ResponseEntity.ok().build();
	}

	// Transferencia Cliente → Proveedor
    @PostMapping("/transfer/client-to-supplier")
    public ResponseEntity<String> transferClientToSupplier(
            @RequestParam Integer clientId,
            @RequestParam Integer supplierId,
            @RequestParam Double amount) {
        try {
            supplierService.transferFromClientToSupplier(clientId, supplierId, amount);
            return ResponseEntity.ok("Transferencia Cliente → Proveedor realizada con éxito");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
	}
    
    @GetMapping("/{proveedorId}/compras")
    public ResponseEntity<List<BuyDollarsDto>> listarComprasPorProveedor(@PathVariable Integer proveedorId) {
        return ResponseEntity.ok(buyDollarsService.listarComprasPorProveedor(proveedorId));
    }


}
