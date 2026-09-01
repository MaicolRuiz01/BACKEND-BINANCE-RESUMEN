package com.binance.web.conciliacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConciliacionSolicitudRepository extends JpaRepository<ConciliacionSolicitud, Long> {

    Optional<ConciliacionSolicitud> findFirstByConsumidaFalseOrderByCreadaEnAsc();

    /**
     * Dedupe: si ya hay una solicitud pendiente para esta cuenta, no crear otra.
     * Agregado agosto 2026 al automatizar quién llama a solicitarConciliacion
     * (antes solo lo disparaba el botón manual de prueba, uno a la vez) — sin
     * esto, una cuenta que se selecciona/deselecciona varias veces mientras el
     * bot está apagado acumularía filas repetidas (mismo riesgo de backlog que
     * ya se documentó para ActivacionSolicitud).
     */
    Optional<ConciliacionSolicitud> findFirstByCuentaAndConsumidaFalse(String cuenta);
}
