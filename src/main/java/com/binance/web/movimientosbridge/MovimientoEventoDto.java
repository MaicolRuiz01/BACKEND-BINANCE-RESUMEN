package com.binance.web.movimientosbridge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Espejo exacto del payload JSON que manda Pochonance/pochonance_bridge.py
 * (Automatizacion Bancolombia / Movimientos) al endpoint POST /movimientos/evento.
 *
 * Cuatro tipos de evento posibles (campo "evento"):
 *  - "movimiento"        → una transacción concreta fue detectada. Trae monto,
 *                           descripcion, referencia, fecha_transaccion.
 *  - "cambio_saldo"      → el saldo cambió pero no se pudo identificar la
 *                           transacción exacta. Trae delta en vez de monto, y no
 *                           trae descripcion/referencia/fecha_transaccion.
 *  - "conexion_exitosa"  → primer login+datos OK de una cuenta recién activada
 *                           en Cuentas P2P (MonitorThread, ver sesiones.py).
 *                           Trae saldo_actual y cantidad_movimientos.
 *  - "error_login"       → primer login fallido de una cuenta recién activada.
 *                           Trae motivo (puede ser null si no se pudo determinar).
 */
@Data
public class MovimientoEventoDto {

    private String evento;

    private String cuenta;

    /** "salida" o "entrada". */
    private String tipo;

    /** Solo presente cuando evento = "movimiento". */
    private Double monto;

    /** Solo presente cuando evento = "cambio_saldo". */
    private Double delta;

    @JsonProperty("saldo_anterior")
    private String saldoAnterior;

    @JsonProperty("saldo_actual")
    private String saldoActual;

    /** Solo presente cuando evento = "movimiento". */
    private String descripcion;

    @JsonProperty("descripcion_corta")
    private String descripcionCorta;

    /** Solo presente cuando evento = "movimiento". */
    private String referencia;

    @JsonProperty("fecha_transaccion")
    private String fechaTransaccion;

    /** Solo presente cuando evento = "conexion_exitosa". */
    @JsonProperty("cantidad_movimientos")
    private Integer cantidadMovimientos;

    /** Solo presente cuando evento = "error_login". Puede venir null. */
    private String motivo;

    private String timestamp;
}
