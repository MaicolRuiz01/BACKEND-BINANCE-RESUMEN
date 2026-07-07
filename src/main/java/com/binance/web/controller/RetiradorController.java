package com.binance.web.controller;

import com.binance.web.Entity.Retirador;
import com.binance.web.Entity.SolicitudRetiro;
import com.binance.web.dto.*;
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
    public List<Retirador> getAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public Retirador getById(@PathVariable Long id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<Retirador> create(@RequestBody Retirador retirador) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(retirador));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Retirador> update(@PathVariable Long id, @RequestBody Retirador body) {
        Retirador existing = service.findById(id);
        existing.setNombre(body.getNombre());
        existing.setTelegramUsername(body.getTelegramUsername());
        if (body.getEfectivo() != null) existing.setEfectivo(body.getEfectivo());
        return ResponseEntity.ok(service.save(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Solicitud con retirador pre-asignado ──────────────────────

    @PostMapping("/solicitar-retiro")
    public ResponseEntity<SolicitudRetiro> crearSolicitud(@RequestBody SolicitudRetiroRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearSolicitud(request));
    }

    @PostMapping("/solicitudes/{solicitudId}/confirmar")
    public ResponseEntity<SolicitudRetiro> confirmar(@PathVariable Long solicitudId) {
        return ResponseEntity.ok(service.confirmarSolicitud(solicitudId));
    }

    @PostMapping("/solicitudes/{solicitudId}/reenviar")
    public ResponseEntity<SolicitudRetiro> reenviar(@PathVariable Long solicitudId) {
        return ResponseEntity.ok(service.reenviarSolicitud(solicitudId));
    }

    @PostMapping("/solicitudes/{solicitudId}/cancelar")
    public ResponseEntity<SolicitudRetiro> cancelarSolicitud(@PathVariable Long solicitudId) {
        return ResponseEntity.ok(service.cancelarSolicitud(solicitudId));
    }

    @GetMapping("/{id}/solicitudes")
    public List<SolicitudRetiro> historial(@PathVariable Long id) {
        return service.historialPorRetirador(id);
    }

    // ── Solicitud general (sin retirador) ─────────────────────────

    @PostMapping("/solicitud-general")
    public ResponseEntity<SolicitudRetiro> crearSolicitudGeneral(@RequestBody SolicitudGeneralRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearSolicitudGeneral(request));
    }

    @GetMapping("/solicitud-general/pendientes")
    public List<SolicitudRetiro> getSinAsignar() {
        return service.getSolicitudesSinAsignar();
    }

    @PostMapping("/solicitud-general/{id}/asignar")
    public ResponseEntity<SolicitudRetiro> asignar(@PathVariable Long id, @RequestBody AsignarRetiradorDto dto) {
        return ResponseEntity.ok(service.asignarRetirador(id, dto));
    }

    // ── Pago al retirador ─────────────────────────────────────────

    @PostMapping("/{id}/pagar")
    public ResponseEntity<Retirador> pagar(@PathVariable Long id, @RequestBody PagoRetiradorDto dto) {
        return ResponseEntity.ok(service.pagarRetirador(id, dto));
    }

    // ── Ranking semanal ───────────────────────────────────────────

    @GetMapping("/ranking/semana")
    public List<RankingRetiradorDto> rankingSemana() {
        return service.getRankingSemana();
    }

    @PostMapping("/{id}/bono")
    public ResponseEntity<Retirador> aplicarBono(
            @PathVariable Long id,
            @RequestParam(defaultValue = "20000") double monto) {
        return ResponseEntity.ok(service.aplicarBono(id, monto));
    }
}
