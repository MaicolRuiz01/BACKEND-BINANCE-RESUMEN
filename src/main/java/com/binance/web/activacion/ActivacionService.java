package com.binance.web.activacion;

import com.binance.web.Entity.AccountCop;

import java.util.Optional;

public interface ActivacionService {

    /** Encola una solicitud de activación de monitoreo para esta cuenta (dedupe si ya hay una pendiente). */
    void solicitarActivacion(AccountCop cuenta);

    /** Consume (marca como atendida) la solicitud pendiente más antigua, si hay alguna. */
    Optional<String> obtenerYConsumirPendiente();

    /** Registra el resultado inmediato que reporta el bot tras intentar arrancar el monitoreo. */
    void procesarResultado(ActivacionResultadoDto resultado);
}
