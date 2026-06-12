package com.binance.web.service;

import com.binance.web.Entity.Retirador;
import com.binance.web.Entity.SolicitudRetiro;
import com.binance.web.dto.*;

import java.util.List;

public interface RetiradorService {

    // ── CRUD retiradores ──────────────────────────────────────────
    List<Retirador> findAll();
    Retirador findById(Long id);
    Retirador save(Retirador retirador);
    void delete(Long id);

    // ── Solicitud con retirador pre-asignado ──────────────────────
    SolicitudRetiro crearSolicitud(SolicitudRetiroRequestDto request);
    SolicitudRetiro confirmarSolicitud(Long solicitudId);
    List<SolicitudRetiro> historialPorRetirador(Long retiradorId);

    // ── Solicitud general (sin retirador) ─────────────────────────
    SolicitudRetiro crearSolicitudGeneral(SolicitudGeneralRequestDto request);
    SolicitudRetiro asignarRetirador(Long solicitudId, AsignarRetiradorDto dto);
    List<SolicitudRetiro> getSolicitudesSinAsignar();

    // ── Pago al retirador ─────────────────────────────────────────
    Retirador pagarRetirador(Long retiradorId, PagoRetiradorDto dto);

    // ── Ranking semanal ───────────────────────────────────────────
    List<RankingRetiradorDto> getRankingSemana();
    Retirador aplicarBono(Long retiradorId, double monto);
}
