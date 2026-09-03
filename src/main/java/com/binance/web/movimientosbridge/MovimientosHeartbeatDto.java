package com.binance.web.movimientosbridge;

import java.util.List;

/**
 * Cuerpo de POST /movimientos/heartbeat — reporte periódico que manda
 * pochonance_activador.py con los nombres de las cuentas que tiene
 * corriendo AHORA MISMO (sesiones de monitoreo vivas, ver _sesiones_activas
 * en iniciar.py). Ver MovimientosHeartbeatService para el porqué existe
 * este endpoint (incidente "Yeiner Rodriguez Ortega", 03/09/2026: una
 * detención se pierde silenciosamente y la cuenta queda corriendo sin que
 * nadie lo note).
 */
public class MovimientosHeartbeatDto {

    private List<String> cuentasActivas;

    public List<String> getCuentasActivas() {
        return cuentasActivas;
    }

    public void setCuentasActivas(List<String> cuentasActivas) {
        this.cuentasActivas = cuentasActivas;
    }
}
