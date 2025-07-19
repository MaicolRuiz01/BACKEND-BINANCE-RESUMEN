package com.binance.web.gastos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.Entity.Gasto;
import com.binance.web.Repository.GastoRepository;

import java.util.List;

@RestController
@RequestMapping("/gastos")
public class GastoController {

    @Autowired
    private GastoService gastoService;
    @Autowired
    private GastoRepository gastoRepository;

    @GetMapping
    public List<Gasto> getAllGastos() {
        return gastoRepository.findAll();
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
    

}
