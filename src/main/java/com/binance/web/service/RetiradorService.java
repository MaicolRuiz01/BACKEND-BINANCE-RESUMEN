package com.binance.web.service;

import com.binance.web.Entity.Retirador;
import com.binance.web.Entity.SolicitudRetiro;
import com.binance.web.dto.SolicitudRetiroRequestDto;

import java.util.List;

public interface RetiradorService {

    // ── CRUD retiradores ──────────────────────────────────────────
    List<Retirador> findAll();
    Retirador findById(Long id);
    Retirador save(Retirador retirador);
    void delete(Long id);

    // ── Solicitudes de retiro ─────────────────────────────────────
    SolicitudRetiro crearSolicitud(SolicitudRetiroRequestDto request);
    SolicitudRetiro confirmarSolicitud(Long solicitudId);
    List<SolicitudRetiro> historialPorRetirador(Long retiradorId);
}
