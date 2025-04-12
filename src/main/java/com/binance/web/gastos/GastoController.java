package com.binance.web.gastos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gastos")
public class GastoController {

    @Autowired
    private GastoService gastoService;

    @GetMapping
    public List<Gasto> getAllGastos() {
        return gastoService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Gasto> getGastoById(@PathVariable Integer id) {
        return gastoService.findById(id)
            .map(gasto -> ResponseEntity.ok(gasto))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public Gasto createGasto(@RequestBody Gasto gasto) {
        return gastoService.save(gasto);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Gasto> updateGasto(@PathVariable Integer id, @RequestBody Gasto gastoDetails) {
        return gastoService.findById(id)
            .map(gasto -> {
                gasto.setTipo(gastoDetails.getTipo());
                gasto.setDescripcion(gastoDetails.getDescripcion());
                gasto.setFecha(gastoDetails.getFecha());
                gasto.setMonto(gastoDetails.getMonto());
                gasto.setPagado(gastoDetails.getPagado());
                gastoService.save(gasto);
                return ResponseEntity.ok(gasto);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGasto(@PathVariable Integer id) {
        return gastoService.findById(id)
            .map(gasto -> {
                gastoService.deleteById(id);
                return ResponseEntity.ok().<Void>build();
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    @PutMapping("/{id}/pagar")
    public ResponseEntity<Gasto> pagar(@PathVariable Integer id) {
        return gastoService.marcarComoPagado(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
