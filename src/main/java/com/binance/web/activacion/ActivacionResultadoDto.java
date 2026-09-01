package com.binance.web.activacion;

import lombok.Data;

/**
 * Body de POST /movimientos/activacion/resultado — lo manda
 * pochonance_activador.py justo después de intentar arrancar (o no) el
 * MonitorThread de una cuenta que salió de GET /movimientos/activacion/pendiente.
 *
 * OJO: "iniciada=true" solo significa "se lanzó el hilo de monitoreo" (o ya
 * estaba corriendo) — NO es todavía la prueba de que la cuenta sirve. Esa
 * prueba real llega después, por separado, vía /movimientos/evento con
 * evento "conexion_exitosa" o "error_login" (ver MovimientosBridgeServiceImpl).
 * "iniciada=false" (típicamente "sin credenciales en Bitwarden") tampoco es
 * un bloqueo del banco — es un problema de la máquina que corre el bot, y
 * por eso este DTO/endpoint NUNCA toca el estado de la cuenta (bloqueada,
 * disponibleBanco, etc.) — solo se registra/loguea para que Milton lo revise.
 */
@Data
public class ActivacionResultadoDto {
    private String cuenta;
    private Boolean iniciada;
    private String error;
}
