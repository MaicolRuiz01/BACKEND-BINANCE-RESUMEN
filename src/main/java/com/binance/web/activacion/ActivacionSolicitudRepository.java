package com.binance.web.activacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActivacionSolicitudRepository extends JpaRepository<ActivacionSolicitud, Long> {

    Optional<ActivacionSolicitud> findFirstByConsumidaFalseOrderByCreadaEnAsc();

    /**
     * Dedupe: si ya hay una solicitud pendiente para esta cuenta, no crear otra.
     * (ConciliacionSolicitud no tiene este chequeo; acá sí porque el bot ya es
     * idempotente del lado de Python — ver _arrancar_cuenta() en
     * pochonance_activador.py — pero evitar filas repetidas de una vez es más
     * prolijo y barato que confiar solo en esa idempotencia.)
     */
    Optional<ActivacionSolicitud> findFirstByCuentaAndConsumidaFalse(String cuenta);
}
