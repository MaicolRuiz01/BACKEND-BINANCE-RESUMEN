package com.binance.web.Repository;

import com.binance.web.Entity.P2PPreAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /** Filas guardadas antes de que existiera la columna pesos_cop (se completan en el poll). */
    List<P2PPreAsignacion> findByPesosCopIsNull();

    /**
     * Pesos "en curso" por cuenta COP: ventas pre-asignadas que todavía no se importaron.
     *
     * Se excluyen las filas cuya venta ya está registrada: esas ya no están "en curso" (su plata
     * ya pasó, o debe pasar, por la venta importada), así que sumarlas contaría dos veces.
     */
    @Query("""
        SELECT p.cuentaCop.id AS copId, COALESCE(SUM(p.pesosCop), 0) AS total
        FROM P2PPreAsignacion p
        WHERE NOT EXISTS (SELECT 1 FROM SaleP2P s WHERE s.numberOrder = p.orderNumber)
        GROUP BY p.cuentaCop.id
    """)
    List<SumaEnCurso> sumarEnCursoPorCuenta();

    interface SumaEnCurso {
        Integer getCopId();
        Double getTotal();
    }
}
