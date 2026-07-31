package com.binance.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.model.DeduccionDto;
import com.binance.web.service.DeduccionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deducciones: ventas P2P registradas a mano (órdenes que quedaron en modo restricción).
 * Se guardan aparte de las ventas P2P, pero sí mueven el saldo de la cuenta COP.
 */
@Slf4j
@RestController
@RequestMapping("/deducciones")
@CrossOrigin("*")
@RequiredArgsConstructor
public class DeduccionController {

    private final DeduccionService deduccionService;

    @GetMapping
    public ResponseEntity<List<DeduccionDto>> listar() {
        return ResponseEntity.ok(deduccionService.listar());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody DeduccionDto dto) {
        try {
            return ResponseEntity.ok(deduccionService.crear(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Deduccion] Error creando: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo crear la deducción"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody DeduccionDto dto) {
        try {
            return ResponseEntity.ok(deduccionService.actualizar(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Deduccion] Error actualizando {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo actualizar la deducción"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            deduccionService.eliminar(id);
            return ResponseEntity.ok(Map.of("mensaje", "Deducción eliminada"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Deduccion] Error eliminando {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo eliminar la deducción"));
        }
    }
}
