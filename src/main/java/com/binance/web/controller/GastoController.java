package com.binance.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.Entity.Gasto;
import com.binance.web.Repository.GastoRepository;
import com.binance.web.service.GastoService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gastos")
public class GastoController {

    @Autowired
    private GastoService gastoService;
    @Autowired
    private GastoRepository gastoRepository;

    @GetMapping
    public List<Map<String, Object>> getAllGastos() {
        // Listado liviano: solo ids de las relaciones → evita serializar la cuenta COP
        // completa (con sus brebeKeys) por cada gasto. Mucho más rápido.
        return gastoRepository.findAllLite().stream().map(v -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", v.getId());
            m.put("descripcion", v.getDescripcion());
            m.put("fecha", v.getFecha());
            m.put("monto", v.getMonto());
            m.put("cuentaPago", v.getCuentaPagoId() != null ? Map.of("id", v.getCuentaPagoId()) : null);
            m.put("pagoEfectivo", v.getPagoEfectivoId() != null ? Map.of("id", v.getPagoEfectivoId()) : null);
            return m;
        }).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Gasto> getGastoById(@PathVariable Integer id) {
        return gastoRepository.findById(id)
            .map(gasto -> ResponseEntity.ok(gasto))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public Gasto createGasto(@RequestBody Gasto gasto) {
        return gastoService.saveGasto(gasto);
        
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Gasto> updateGasto(@PathVariable Integer id, @RequestBody Gasto gastoDetails) {
        return gastoRepository.findById(id)
            .map(gasto -> {
                gasto.setDescripcion(gastoDetails.getDescripcion());
                gasto.setFecha(gastoDetails.getFecha());
                gasto.setMonto(gastoDetails.getMonto());
                gastoService.saveGasto(gasto);
                return ResponseEntity.ok(gasto);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGasto(@PathVariable Integer id) {
        return gastoRepository.findById(id)
            .map(gasto -> {
            	gastoRepository.deleteById(id);
                return ResponseEntity.ok().<Void>build();
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    @GetMapping("/total-hoy/cuenta-cop/{id}")
    public ResponseEntity<Double> getTotalGastosHoyCuentaCop(@PathVariable Integer id) {
        return ResponseEntity.ok(gastoService.totalGastosHoyCuentaCop(id));
    }

    @GetMapping("/total-hoy/caja/{id}")
    public ResponseEntity<Double> getTotalGastosHoyCaja(@PathVariable Integer id) {
        return ResponseEntity.ok(gastoService.totalGastosHoyCaja(id));
    }


}
