package com.binance.web.conciliacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConciliacionSolicitudRepository extends JpaRepository<ConciliacionSolicitud, Long> {

    Optional<ConciliacionSolicitud> findFirstByConsumidaFalseOrderByCreadaEnAsc();
}
