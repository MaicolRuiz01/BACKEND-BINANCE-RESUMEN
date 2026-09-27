package com.binance.web.movimientosbridge;

import java.util.Optional;

/**
 * Procesa los eventos que manda pochonance_bridge.py (sistema de Movimientos)
 * y los reenvía formateados a los chats de confianza a través del bot
 * "Cuentas P2P".
 */
public interface MovimientosBridgeService {

    void procesarEvento(MovimientoEventoDto evento);

    /**
     * Escenario 2 (25/09/2026): resultado de entrega de un evento ya
     * procesado, identificado por el "evento_id" que vino en el payload.
     * Optional vacío = todavía no hay resultado (evento no encontrado, no
     * traía evento_id, o ya expiró del caché) — Python lo interpreta como
     * "sigue pendiente, seguir esperando dentro del timer".
     */
    Optional<Boolean> consultarEstadoEntrega(String eventoId);
}
