package com.binance.web.pagoproveedor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.PagoProveedor;

@RestController
@RequestMapping("/pago-proveedor")
public class PagoProveedorController {
	
	private final PagoProveedorService pagoProveedorService;

    public PagoProveedorController(PagoProveedorService pagoProveedorService) {
        this.pagoProveedorService = pagoProveedorService;
    }

    // Endpoint para registrar un pago
    @PostMapping("/hecho")
    public ResponseEntity<PagoProveedor> makePayment(@RequestParam Integer accountCopId,
                                               @RequestParam Integer supplierId,
                                               @RequestParam Double amount) {
        try {
            PagoProveedor payment = pagoProveedorService.makePayment(accountCopId, supplierId, amount);
            return ResponseEntity.status(HttpStatus.CREATED).body(payment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null); // En caso de error
        }
    }
    @GetMapping
    public ResponseEntity<List<PagoProveedor>> getAllPagos(){
    	List<PagoProveedor> pagos = pagoProveedorService.findAllPagos();
    	return ResponseEntity.ok(pagos);
    }
    
    @GetMapping("/por-supplier/{supplierId}")
    public ResponseEntity<List<PagoProveedor>> getPagosBySupplier(@PathVariable Integer supplierId) {
        List<PagoProveedor> pagos = pagoProveedorService.getPagosBySupplierId(supplierId);
        return ResponseEntity.ok(pagos);
    }

}
