package com.binance.web.Repository;

import com.binance.web.Entity.P2PPreAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface P2PPreAsignacionRepository extends JpaRepository<P2PPreAsignacion, Long> {

    Optional<P2PPreAsignacion> findByOrderNumber(String orderNumber);

    void deleteByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    /**
     * Pre-asignaciones creadas antes de una fecha. Se usa para limpiar las que quedaron
     * huérfanas: órdenes que se cancelaron o se cayeron y por lo tanto nunca se completaron,
     * así que su fila no se borra nunca por la vía normal.
     */
    List<P2PPreAsignacion> findByCreatedAtBefore(LocalDateTime fecha);
}
