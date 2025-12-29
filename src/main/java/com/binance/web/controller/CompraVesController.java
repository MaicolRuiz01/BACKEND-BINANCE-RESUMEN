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

import com.binance.web.Entity.CompraVES;
import com.binance.web.service.CompraVesService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/compra-ves")
@RequiredArgsConstructor
public class CompraVesController {

    private final CompraVesService service;

    @GetMapping
    public List<CompraVES> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompraVES> get(@PathVariable Long id) {
        CompraVES c = service.findById(id);
        return c != null ? ResponseEntity.ok(c) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<CompraVES> create(@RequestBody CompraVES compra) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(compra));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CompraVES> update(@PathVariable Long id, @RequestBody CompraVES compra) {
        return ResponseEntity.ok(service.update(id, compra));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dia")
    public List<CompraVES> listByDay(@RequestParam String day) {
        // day: "2025-12-14"
        LocalDate d = LocalDate.parse(day);
        return service.findByDay(d);
    }
}
