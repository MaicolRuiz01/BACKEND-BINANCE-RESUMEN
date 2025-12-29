package com.binance.web.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.VentaVES;
import com.binance.web.service.VentaVesService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/venta-ves")
@RequiredArgsConstructor
public class VentaVesController {

    private final VentaVesService service;

    @GetMapping
    public List<VentaVES> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<VentaVES> get(@PathVariable Long id) {
        VentaVES v = service.findById(id);
        return v != null ? ResponseEntity.ok(v) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<VentaVES> create(@RequestBody VentaVES venta) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(venta));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VentaVES> update(@PathVariable Long id, @RequestBody VentaVES venta) {
        return ResponseEntity.ok(service.update(id, venta));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dia")
    public List<VentaVES> listByDay(@RequestParam String day) {
        LocalDate d = LocalDate.parse(day);
        return service.findByDay(d);
    }
}
