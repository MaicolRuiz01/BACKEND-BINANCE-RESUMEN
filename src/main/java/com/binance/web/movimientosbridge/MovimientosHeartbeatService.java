package com.binance.web.movimientosbridge;

import java.util.List;

public interface MovimientosHeartbeatService {

    /**
     * Reconcilia el reporte periódico de Movimientos (cuentas que tiene
     * corriendo AHORA MISMO) contra el estado real en AccountCop.activaParaP2P.
     *
     * - Si una cuenta sigue corriendo en Movimientos pero ya no está activa
     *   en P2P (la orden de detenerla se perdió), reencola su detención.
     * - Si una cuenta está activa en P2P pero Movimientos no la reporta
     *   corriendo (la orden de activarla se perdió), reencola su activación.
     *
     * Nunca lanza excepción hacia afuera — un heartbeat es best-effort, un
     * fallo acá no debe tumbar el ciclo de polling del bot ni el endpoint.
     */
    void reconciliar(List<String> cuentasActivasReportadas);
}
