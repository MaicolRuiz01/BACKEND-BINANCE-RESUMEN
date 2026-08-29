package com.binance.web.detencion;

import lombok.Data;

/**
 * Body de POST /movimientos/detencion/resultado — lo manda el bot de
 * Movimientos justo después de intentar parar (o no) el MonitorThread de
 * una cuenta que salió de GET /movimientos/detencion/pendiente.
 *
 * "detenida=true" significa que se llamó stop() sobre esa sesión (o que ya
 * no estaba corriendo, así que no había nada que parar). "detenida=false"
 * (típicamente una excepción al llamar stop()) solo se loguea para revisión
 * manual — este DTO/endpoint NUNCA reactiva ni bloquea nada por su cuenta.
 */
@Data
public class DetencionResultadoDto {
    private String cuenta;
    private Boolean detenida;
    private String error;
}
