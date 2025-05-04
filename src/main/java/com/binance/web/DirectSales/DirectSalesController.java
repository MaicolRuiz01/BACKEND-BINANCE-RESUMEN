package com.binance.web.DirectSales;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.Entity.DirectSales;

import java.util.List;

@RestController
@RequestMapping("/api/direct-sales")
public class DirectSalesController {

    @Autowired
    private DirectSalesService service;

    @GetMapping
    public List<DirectSales> getAllDirectSales() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DirectSales> getDirectSalesById(@PathVariable Integer id) {
        return service.findById(id)
                      .map(ResponseEntity::ok)
                      .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public DirectSales createDirectSales(@RequestBody DirectSales directSales) {
        return service.save(directSales);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DirectSales> updateDirectSales(@PathVariable Integer id, @RequestBody DirectSales directSalesDetails) {
        return service.findById(id)
                      .map(existingDirectSales -> {
                          existingDirectSales.setFechaRegistro(directSalesDetails.getFechaRegistro());
                          existingDirectSales.setTasaCompra(directSalesDetails.getTasaCompra());
                          existingDirectSales.setTasaVenta(directSalesDetails.getTasaVenta());
                          return ResponseEntity.ok(service.save(existingDirectSales));
                      })
                      .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDirectSales(@PathVariable Integer id) {
        return service.findById(id)
                      .map(directSales -> {
                          service.deleteById(id);
                          return ResponseEntity.ok().<Void>build();
                      })
                      .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
