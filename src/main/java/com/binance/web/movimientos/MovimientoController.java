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
    public Movimiento deposito(@RequestParam Integer cuentaId, @RequestParam Double monto) {
        return movimientoService.RegistrarDeposito(cuentaId, monto);
    }

    @PostMapping("/retiro")
    public Movimiento retiro(@RequestParam Integer cuentaId, @RequestParam Double monto) {
        return movimientoService.RegistrarRetiro(cuentaId, monto);
    }

    @PostMapping("/transferencia")
    public Movimiento transferencia(@RequestParam Integer origenId, @RequestParam Integer destinoId, @RequestParam Double monto) {
        return movimientoService.RegistrarTransferencia(origenId, destinoId, monto);
    }

    @GetMapping("/listar")
    public List<Movimiento> listar() {
        return movimientoService.listar();
    }
}
