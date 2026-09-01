package com.binance.web.detencion;

import com.binance.web.Entity.AccountCop;

import java.util.Optional;

public interface DetencionService {

    /** Encola una solicitud de detención de monitoreo para esta cuenta (dedupe si ya hay una pendiente). */
    void solicitarDetencion(AccountCop cuenta);

    /** Consume (marca como atendida) la solicitud pendiente más antigua, si hay alguna. */
    Optional<String> obtenerYConsumirPendiente();

    /** Registra el resultado inmediato que reporta el bot tras intentar parar el monitoreo. */
    void procesarResultado(DetencionResultadoDto resultado);
}
