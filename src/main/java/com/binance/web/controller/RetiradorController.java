package com.binance.web.controller;

import com.binance.web.Entity.Retirador;
import com.binance.web.Entity.SolicitudRetiro;
import com.binance.web.dto.PagoRetiradorDto;
import com.binance.web.dto.SolicitudRetiroRequestDto;
import com.binance.web.service.RetiradorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/retiradores")
@CrossOrigin(origins = "*")
public class RetiradorController {

    private final RetiradorService service;

    public RetiradorController(RetiradorService service) {
        this.service = service;
    }

    // ── CRUD retiradores ──────────────────────────────────────────

    @GetMapping
    public List<Retirador> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Retirador getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<Retirador> create(@RequestBody Retirador retirador) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(retirador));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Retirador> update(@PathVariable Long id, @RequestBody Retirador body) {
        Retirador existing = service.findById(id);
        existing.setNombre(body.getNombre());
        if (body.getEfectivo() != null) existing.setEfectivo(body.getEfectivo());
        return ResponseEntity.ok(service.save(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Solicitudes de retiro ─────────────────────────────────────

    @PostMapping("/solicitar-retiro")
    public ResponseEntity<SolicitudRetiro> crearSolicitud(@RequestBody SolicitudRetiroRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearSolicitud(request));
    }

    @PostMapping("/solicitudes/{solicitudId}/confirmar")
    public ResponseEntity<SolicitudRetiro> confirmar(@PathVariable Long solicitudId) {
        return ResponseEntity.ok(service.confirmarSolicitud(solicitudId));
    }

    @GetMapping("/{id}/solicitudes")
    public List<SolicitudRetiro> historial(@PathVariable Long id) {
        return service.historialPorRetirador(id);
    }

    // ── Pago al retirador ─────────────────────────────────────────

    @PostMapping("/{id}/pagar")
    public ResponseEntity<Retirador> pagar(@PathVariable Long id, @RequestBody PagoRetiradorDto dto) {
        return ResponseEntity.ok(service.pagarRetirador(id, dto));
    }
}
