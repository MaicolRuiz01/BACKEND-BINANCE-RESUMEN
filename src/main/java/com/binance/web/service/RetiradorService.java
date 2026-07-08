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

    /**
     * Reintenta la notificación privada de Telegram de una solicitud
     * individual ya creada (útil cuando el retirador no había vinculado su
     * chat con /start en el momento de crearla). No modifica saldos ni
     * crea una solicitud nueva.
     */
    SolicitudRetiro reenviarSolicitud(Long solicitudId);

    /**
     * Cancela una solicitud que todavía no fue completada (SIN_ASIGNAR o
     * PENDIENTE), por ejemplo si se envió por error. No revierte ningún saldo
     * porque el dinero solo se descuenta al confirmar (COMPLETADO), así que
     * cancelar antes de eso no tiene efecto sobre las cuentas ni la caja.
     */
    SolicitudRetiro cancelarSolicitud(Long solicitudId);
    // ═══════════════════════════════════════════════════════════════
    // Solicitud general (sin retirador)
    // ═══════════════════════════════════════════════════════════════
    SolicitudRetiro crearSolicitudGeneral(SolicitudGeneralRequestDto request);
    SolicitudRetiro asignarRetirador(Long solicitudId, AsignarRetiradorDto dto);
    List<SolicitudRetiro> getSolicitudesSinAsignar();
    
    /** Reenvía la solicitud general al grupo (ej: si fue cancelada por un retirador) */
    void reenviarSolicitudGeneral(SolicitudRetiro solicitud);

    // ── Pago al retirador ─────────────────────────────────────────
    Retirador pagarRetirador(Long retiradorId, PagoRetiradorDto dto);

    // ── Ranking semanal ───────────────────────────────────────────
    List<RankingRetiradorDto> getRankingSemana();
    Retirador aplicarBono(Long retiradorId, double monto);

    /** Envía (o reenvía) el recordatorio de caja a un retirador puntual, con botón "Entregar efectivo". */
    void enviarRecordatorioCaja(Retirador retirador);
}
