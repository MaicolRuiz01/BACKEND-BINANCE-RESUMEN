package com.binance.web.movimientosbridge;

/**
 * Procesa los eventos que manda pochonance_bridge.py (sistema de Movimientos)
 * y los reenvía formateados a los chats de confianza a través del bot
 * "Cuentas P2P".
 */
public interface MovimientosBridgeService {

    void procesarEvento(MovimientoEventoDto evento);
}
