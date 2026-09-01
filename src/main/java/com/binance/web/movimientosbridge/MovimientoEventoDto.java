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

    /**
     * Solo presente cuando evento = "conexion_exitosa". Bloque YA formateado
     * (emoji rojo/verde, fecha, descripcion, monto — uno por linea, hasta 8)
     * con el mismo formato exacto del primer mensaje que manda iniciar.py al
     * chat de Movimientos (ver _esperar_y_notificar en iniciar.py). Se arma
     * en Python (pochonance_bridge.py), no en Java, para no duplicar la
     * logica de formato de fecha/monto en dos lenguajes distintos — Python
     * sigue siendo la unica fuente de verdad de como se ve un movimiento.
     */
    @JsonProperty("movimientos_texto")
    private String movimientosTexto;

    /** Solo presente cuando evento = "error_login". Puede venir null. */
    private String motivo;

    /**
     * Lista de chat IDs (separados por coma) que deben recibir este evento en
     * el bot "Cuentas P2P" — se arma en Python a partir de chats_id.json
     * (Movimientos/notificar.py → _leer_chat_ids), para que Cuentas P2P
     * notifique EXACTAMENTE a los mismos chats que ya usa el sistema de
     * Movimientos, en vez de mantener una lista aparte y fija en
     * application-prod.properties (a pedido de Milton, 31/08/2026). Si viene
     * null/vacío (p.ej. una prueba manual sin este campo), el backend cae de
     * vuelta a app.cuentasp2p.chats-confiables.
     */
    @JsonProperty("chats_confiables")
    private String chatsConfiables;

    /**
     * Subconjunto de chatsConfiables (separados por coma) que debe recibir el
     * mensaje COMPLETO en vez del resumido — mismo criterio que FULL_NOTIF_IDS
     * en Movimientos/config.py, usado por notificar.py → _notificar_tx. Solo
     * aplica al evento "movimiento" (ver formatearMovimientoCorto); para los
     * demás tipos de evento no hay versión resumida, siempre se manda el
     * mensaje completo. Si viene null/vacío, se trata a TODOS los chats como
     * si estuvieran en la lista completa (compatibilidad con pruebas manuales
     * que no manden este campo).
     */
    @JsonProperty("chats_full")
    private String chatsFull;

    private String timestamp;
}
