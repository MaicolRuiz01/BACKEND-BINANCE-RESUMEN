package com.binance.web.impuesto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/impuestos")
public class ImpuestoController {

    @Autowired
    private ImpuestoService impuestoService;

    @GetMapping
    public List<Impuesto> getAllImpuestos() {
        return impuestoService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Impuesto> getImpuestoById(@PathVariable Integer id) {
        return impuestoService.findById(id)
            .map(impuesto -> ResponseEntity.ok(impuesto))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public Impuesto createImpuesto(@RequestBody Impuesto impuesto) {
        return impuestoService.save(impuesto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Impuesto> updateImpuesto(@PathVariable Integer id, @RequestBody Impuesto impuestoDetails) {
        return impuestoService.findById(id)
            .map(impuesto -> {
                impuesto.setNombre(impuestoDetails.getNombre());
                impuesto.setCosto(impuestoDetails.getCosto());
                impuestoService.save(impuesto);
                return ResponseEntity.ok(impuesto);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteImpuesto(@PathVariable Integer id) {
        return impuestoService.findById(id)
            .map(impuesto -> {
                impuestoService.deleteById(id);
                return ResponseEntity.ok().<Void>build();
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

