package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.Movimiento;
import com.binance.web.movimientos.MovimientoDTO;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Integer> {

	List<Movimiento> findByTipo(String tipo);
	List<Movimiento> findByTipoStartingWithOrderByFechaDesc(String prefijoTipo);

	/** Retiros con 4x1000 pendiente (Bancolombia) hechos antes de la fecha dada → para el scheduler. */
	List<Movimiento> findByComisionAplicadaFalseAndFechaBefore(LocalDateTime limite);

	/** Suma del 4x1000 AÚN pendiente de retiros de cuentas Bancolombia (para el balance). */
	@Query("SELECT COALESCE(SUM(m.comision), 0) FROM Movimiento m "
	     + "WHERE m.comisionAplicada = false AND m.tipo LIKE 'RETIRO%' "
	     + "AND m.cuentaOrigen.bankType = com.binance.web.Entity.BankType.BANCOLOMBIA")
	double sumComisionPendienteBancolombia();

	/**
	 * Proyección liviana de los movimientos de una caja (origen o destino) en UNA sola
	 * consulta con joins, trayendo solo los nombres. Evita el N+1 del EAGER (cuentas COP
	 * con sus llaves Brebe, etc.), que hacía lentísima la carga contra la BD remota.
	 */
	@Query("SELECT new com.binance.web.movimientos.MovimientoDTO(" +
	       "m.id, m.tipo, m.fecha, m.monto, " +
	       "co.name, cd.name, cj.name, cjd.name, pc.nombre, pp.name) " +
	       "FROM Movimiento m " +
	       "LEFT JOIN m.cuentaOrigen co " +
	       "LEFT JOIN m.cuentaDestino cd " +
	       "LEFT JOIN m.caja cj " +
	       "LEFT JOIN m.cajaDestino cjd " +
	       "LEFT JOIN m.pagoCliente pc " +
	       "LEFT JOIN m.pagoProveedor pp " +
	       "WHERE cj.id = :cajaId OR cjd.id = :cajaId " +
	       "ORDER BY m.fecha DESC")
	List<MovimientoDTO> findMovimientosCajaLite(@Param("cajaId") Integer cajaId);
	List<Movimiento> findByTipoAndPagoProveedor_Id(String tipo, Integer proveedorId);
	List<Movimiento> findByTipoAndPagoCliente_Id(String tipo, Integer clienteId);
    List<Movimiento> findByCuentaOrigenIdOrCuentaDestinoId(Integer cuentaOrigenId, Integer cuentaDestinoId);

	List<Movimiento> findByPagoCliente_Id(Integer clienteId);
	List<Movimiento> findByPagoCliente_IdOrClienteOrigen_IdOrderByFechaDesc(Integer clienteId1, Integer clienteId2);
	List<Movimiento> findByCaja_IdOrderByFechaDesc(Integer cajaId);
	List<Movimiento> findByCaja_IdOrCajaDestino_IdOrderByFechaDesc(Integer cajaId1, Integer cajaId2);
	List<Movimiento> findByCuentaOrigen_IdOrCuentaDestino_IdOrderByFechaDesc(Integer cuentaId1, Integer cuentaId2);
	
	List<Movimiento> findByAjusteCliente_IdOrderByFechaDesc(Integer clienteId);
    List<Movimiento> findByAjusteProveedor_IdOrderByFechaDesc(Integer proveedorId);
    List<Movimiento> findByAjusteCuentaCop_IdOrderByFechaDesc(Integer cuentaId);
    List<Movimiento> findByPagoProveedor_IdOrProveedorOrigen_IdOrderByFechaDesc(Integer pagoProveedorId, Integer proveedorOrigenId);
    List<Movimiento> findByAjusteCliente_IdAndFechaBetween(
            Integer clienteId,
            LocalDateTime desde,
            LocalDateTime hasta
    );

    List<Movimiento> findByAjusteProveedor_IdAndFechaBetween(
            Integer proveedorId,
            LocalDateTime desde,
            LocalDateTime hasta
    );

    List<Movimiento> findByCaja_IdAndTipoOrderByFechaDesc(Integer cajaId, String tipo);
    List<Movimiento> findByAjusteCuentaCop_IdAndFechaBetween(
            Integer cuentaId,
            LocalDateTime desde,
            LocalDateTime hasta
    );

}
