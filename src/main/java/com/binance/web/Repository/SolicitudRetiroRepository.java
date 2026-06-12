package com.binance.web.Repository;

import com.binance.web.Entity.EstadoSolicitud;
import com.binance.web.Entity.SolicitudRetiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SolicitudRetiroRepository extends JpaRepository<SolicitudRetiro, Long> {

    List<SolicitudRetiro> findByRetiradorIdOrderByFechaCreacionDesc(Long retiradorId);

    List<SolicitudRetiro> findByEstadoOrderByFechaCreacionDesc(EstadoSolicitud estado);

    /** Top retiradores de la semana: suma de totalMonto de solicitudes COMPLETADAS entre fechas */
    @Query("""
        SELECT s.retirador.id, SUM(s.totalMonto)
        FROM SolicitudRetiro s
        WHERE s.estado = 'COMPLETADO'
          AND s.retirador IS NOT NULL
          AND s.fechaCreacion >= :desde
          AND s.fechaCreacion < :hasta
        GROUP BY s.retirador.id
        ORDER BY SUM(s.totalMonto) DESC
    """)
    List<Object[]> rankingPorMonto(@Param("desde") LocalDateTime desde,
                                   @Param("hasta") LocalDateTime hasta);
}
