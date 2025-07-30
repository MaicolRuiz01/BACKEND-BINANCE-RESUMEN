package com.binance.web.movimientos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.Movimiento;

@RestController
@RequestMapping("/movimiento")
public class MovimientoController {
	
	@Autowired
    private MovimientoService movimientoService;

    @PostMapping("/deposito")
    public Movimiento deposito(@RequestParam Integer cuentaId,@RequestParam Integer cajaId, @RequestParam Double monto) {
        return movimientoService.RegistrarDeposito(cuentaId,cajaId, monto);
    }

    @PostMapping("/retiro")
    public Movimiento retiro(@RequestParam Integer cuentaId,@RequestParam Integer cajaId, @RequestParam Double monto) {
        return movimientoService.RegistrarRetiro(cuentaId,cajaId, monto);
    }

    @PostMapping("/transferencia")
    public Movimiento transferencia(@RequestParam Integer origenId, @RequestParam Integer destinoId, @RequestParam Double monto) {
        return movimientoService.RegistrarTransferencia(origenId, destinoId, monto);
    }
    @PostMapping("/pago")
    public Movimiento pago(@RequestParam Integer cuentaId, @RequestParam Integer clienteId, @RequestParam Double monto) {
    	return movimientoService.registrarPagoCliente(cuentaId, clienteId, monto);
    }

    @GetMapping("/listar")
    public List<Movimiento> listar() {
        return movimientoService.listar();
    }
    
    @GetMapping("/retiros")
    public List<MovimientoDTO> listarRetiros() {
        return movimientoService.listarRetiros().stream()
            .map(this::mapToDto)
            .toList();
    }

    @GetMapping("/depositos")
    public List<MovimientoDTO> listarDepositos() {
        return movimientoService.listarDepositos().stream()
            .map(this::mapToDto)
            .toList();
    }

    @GetMapping("/transferencias")
    public List<MovimientoDTO> listarTransferencias() {
        return movimientoService.listarTransferencias().stream()
            .map(this::mapToDto)
            .toList();
    }
    
    @GetMapping("/pagos")
    public List<MovimientoDTO> listarPagos(){
    	return movimientoService.listarPagos().stream()
    			.map(this::mapToDto)
    			.toList();
    }
    
    private MovimientoDTO mapToDto(Movimiento movimiento) {
	    return new MovimientoDTO(
	        movimiento.getTipo(),
	        movimiento.getFecha(),
	        movimiento.getMonto(),
	        movimiento.getCuentaOrigen() != null ? movimiento.getCuentaOrigen().getName() : null,
	        movimiento.getCuentaDestino() != null ? movimiento.getCuentaDestino().getName() : null,
	        movimiento.getCaja() != null ? movimiento.getCaja().getName() : null,
	        movimiento.getPagoCliente() != null ? movimiento.getPagoCliente().getNombre() : null
	    );
	}
}
