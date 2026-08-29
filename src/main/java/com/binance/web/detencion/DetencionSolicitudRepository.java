package com.binance.web.detencion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DetencionSolicitudRepository extends JpaRepository<DetencionSolicitud, Long> {

    Optional<DetencionSolicitud> findFirstByConsumidaFalseOrderByCreadaEnAsc();

    /** Dedupe: si ya hay una solicitud pendiente para esta cuenta, no crear otra. */
    Optional<DetencionSolicitud> findFirstByCuentaAndConsumidaFalse(String cuenta);
}
