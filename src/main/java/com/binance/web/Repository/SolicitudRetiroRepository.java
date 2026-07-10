package com.binance.web.Repository;

import com.binance.web.Entity.DetalleRetiro;
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

    /** Solicitudes aún sin confirmar (SIN_ASIGNAR o PENDIENTE) creadas antes del límite dado — para expirarlas automáticamente. */
    List<SolicitudRetiro> findByEstadoInAndFechaCreacionBefore(List<EstadoSolicitud> estados, LocalDateTime limite);

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

    // ═══════════════════════════════════════════════════════════════
    // Monto "comprometido" por cuenta: dinero de solicitudes YA enviadas
    // (SIN_ASIGNAR o PENDIENTE) que todavía no fueron confirmadas por el
    // retirador. El saldo de la cuenta no se descuenta hasta confirmar, así
    // que esto sirve para saber cuánto de ese saldo ya está "reservado".
    // ═══════════════════════════════════════════════════════════════

    /** Total comprometido de UNA cuenta puntual (usado al validar una nueva solicitud). */
    @Query("""
        SELECT COALESCE(SUM(COALESCE(d.montoCajero, 0) + COALESCE(d.montoCorresponsal, 0)), 0)
        FROM DetalleRetiro d
        WHERE d.cuentaCop.id = :cuentaCopId
          AND d.solicitud.estado IN ('SIN_ASIGNAR', 'PENDIENTE')
    """)
    Double sumComprometidoPorCuenta(@Param("cuentaCopId") Integer cuentaCopId);

    /**
     * Igual que sumComprometidoPorCuenta pero separado por tipo (CAJERO / CORRESPONSAL),
     * para poder validar el cupo diario de cada tipo por separado: dos solicitudes
     * pendientes de la misma cuenta no deben poder sumar, entre ambas, más del cupo
     * diario de cajero o de corresponsal, aunque ninguna se haya confirmado todavía.
     */
    @Query("""
        SELECT COALESCE(SUM(COALESCE(d.montoCajero, 0)), 0)
        FROM DetalleRetiro d
        WHERE d.cuentaCop.id = :cuentaCopId
          AND d.solicitud.estado IN ('SIN_ASIGNAR', 'PENDIENTE')
    """)
    Double sumMontoCajeroComprometidoPorCuenta(@Param("cuentaCopId") Integer cuentaCopId);

    @Query("""
        SELECT COALESCE(SUM(COALESCE(d.montoCorresponsal, 0)), 0)
        FROM DetalleRetiro d
        WHERE d.cuentaCop.id = :cuentaCopId
          AND d.solicitud.estado IN ('SIN_ASIGNAR', 'PENDIENTE')
    """)
    Double sumMontoCorresponsalComprometidoPorCuenta(@Param("cuentaCopId") Integer cuentaCopId);

    /** Detalle completo de todas las solicitudes pendientes (para armar el desglose por cuenta). */
    @Query("""
        SELECT d FROM DetalleRetiro d
        JOIN FETCH d.solicitud s
        LEFT JOIN FETCH s.retirador
        WHERE s.estado IN ('SIN_ASIGNAR', 'PENDIENTE')
        ORDER BY s.fechaCreacion DESC
    """)
    List<DetalleRetiro> findDetallesComprometidos();
}
