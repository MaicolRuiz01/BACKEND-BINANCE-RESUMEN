package com.binance.web.movimientos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public Movimiento deposito(@RequestParam Integer cuentaId, @RequestParam Integer cajaId,
            @RequestParam Double monto) {
        return movimientoService.RegistrarDeposito(cuentaId, cajaId, monto);
    }

    @PostMapping("/retiro")
    public Movimiento retiro(@RequestParam Integer cuentaId, @RequestParam Integer cajaId, @RequestParam Double monto) {
        return movimientoService.RegistrarRetiro(cuentaId, cajaId, monto);
    }

    @PostMapping("/transferencia")
    public Movimiento transferencia(@RequestParam Integer origenId, @RequestParam Integer destinoId,
            @RequestParam Double monto) {
        return movimientoService.RegistrarTransferencia(origenId, destinoId, monto);
    }

    @PostMapping("/pago")
    public Movimiento pago(@RequestParam Integer cuentaId, @RequestParam Integer clienteId,
            @RequestParam Double monto) {
        return movimientoService.registrarPagoCliente(cuentaId, clienteId, monto);
    }

    @PostMapping("/pago-proveedor")
    public Movimiento pagoProveedor(
        @RequestParam(required = false) Integer cuentaId,
        @RequestParam(required = false) Integer caja,
        @RequestParam(required = false) Integer proveedorOrigen,
        @RequestParam(required = false) Integer clienteId,
        @RequestParam Integer proveedor,
        @RequestParam Double monto
    ) {
        return movimientoService.registrarPagoProveedor(cuentaId, caja, proveedorOrigen, proveedor, clienteId, monto);
    }
    @PostMapping("/pago-caja")
    public Movimiento pagoClienteCaja(@RequestParam Integer clienteId, @RequestParam Integer cajaId, @RequestParam Double monto) {
    	return movimientoService.registrarPagoCaja(clienteId, cajaId, monto);
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
    public List<MovimientoDTO> listarPagos() {
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
                movimiento.getPagoCliente() != null ? movimiento.getPagoCliente().getNombre() : null,
                movimiento.getPagoProveedor() != null ? movimiento.getPagoProveedor().getName() : null);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Movimiento> actualizarMovimiento(
            @PathVariable Integer id,
            @RequestBody MovimientoUpdateDTO dto) {

        Movimiento actualizado = movimientoService.actualizarMovimiento(
                id,
                dto.getMonto(),
                dto.getCuentaOrigenId(),
                dto.getCuentaDestinoId(),
                dto.getCajaId());
        return ResponseEntity.ok(actualizado);
    }

    @GetMapping("/pagos-proveedor/{proveedorId}")
    public List<MovimientoDTO> listarPagosProveedorPorId(@PathVariable Integer proveedorId) {
        return movimientoService.listarPagosProveedorPorId(proveedorId).stream()
                .map(this::mapToDto)
                .toList();
    }

    @GetMapping("/pagos-cliente/test")
        public String test() {
    return "MovimientoController activo";
}
    @GetMapping("/cliente/{clienteId}")
    public List<MovimientoDTO> listarMovimientosPorCliente(@PathVariable Integer clienteId) {
        return movimientoService.listarMovimientosPorCliente(clienteId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

}
