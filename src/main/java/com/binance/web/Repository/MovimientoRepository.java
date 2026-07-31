package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
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

	/** Suma del 4x1000 AÚN pendiente de CUALQUIER salida de cuentas Bancolombia (para el balance).
	 *  comisionAplicada=false + banco BANCOLOMBIA ya identifica un 4x1000 diferido pendiente,
	 *  sin importar el tipo (retiro, pago proveedor, pago retirador, etc.). */
	@Query("SELECT COALESCE(SUM(m.comision), 0) FROM Movimiento m "
	     + "WHERE m.comisionAplicada = false "
	     + "AND m.cuentaOrigen.bankType = com.binance.web.Entity.BankType.BANCOLOMBIA")
	double sumComisionPendienteBancolombia();

	/**
	 * Proyección liviana de los movimientos de una caja (origen o destino) en UNA sola
	 * consulta con joins, trayendo solo los nombres. Evita el N+1 del EAGER (cuentas COP
	 * con sus llaves Brebe, etc.), que hacía lentísima la carga contra la BD remota.
	 */
	@Query("SELECT new com.binance.web.movimientos.MovimientoDTO(" +
	       "m.id, m.tipo, m.fecha, m.monto, " +
	       "co.name, cd.name, cj.name, cjd.name, COALESCE(pc.nombre, cor.nombre), COALESCE(pp.name, po.name), m.motivo, " +
	       "m.saldoCajaResultante, m.saldoCajaDestinoResultante, co.id, cd.id) " +
	       "FROM Movimiento m " +
	       "LEFT JOIN m.cuentaOrigen co " +
	       "LEFT JOIN m.cuentaDestino cd " +
	       "LEFT JOIN m.caja cj " +
	       "LEFT JOIN m.cajaDestino cjd " +
	       "LEFT JOIN m.pagoCliente pc " +
	       "LEFT JOIN m.clienteOrigen cor " +
	       "LEFT JOIN m.pagoProveedor pp " +
	       "LEFT JOIN m.proveedorOrigen po " +
	       "WHERE cj.id = :cajaId OR cjd.id = :cajaId " +
	       "ORDER BY m.fecha DESC")
	List<MovimientoDTO> findMovimientosCajaLite(@Param("cajaId") Integer cajaId);

	/** Igual que findMovimientosCajaLite pero acotado a un rango de fechas (para el bot de Telegram). */
	@Query("SELECT new com.binance.web.movimientos.MovimientoDTO(" +
	       "m.id, m.tipo, m.fecha, m.monto, " +
	       "co.name, cd.name, cj.name, cjd.name, COALESCE(pc.nombre, cor.nombre), COALESCE(pp.name, po.name), m.motivo, " +
	       "m.saldoCajaResultante, m.saldoCajaDestinoResultante, co.id, cd.id) " +
	       "FROM Movimiento m " +
	       "LEFT JOIN m.cuentaOrigen co " +
	       "LEFT JOIN m.cuentaDestino cd " +
	       "LEFT JOIN m.caja cj " +
	       "LEFT JOIN m.cajaDestino cjd " +
	       "LEFT JOIN m.pagoCliente pc " +
	       "LEFT JOIN m.clienteOrigen cor " +
	       "LEFT JOIN m.pagoProveedor pp " +
	       "LEFT JOIN m.proveedorOrigen po " +
	       "WHERE (cj.id = :cajaId OR cjd.id = :cajaId) AND m.fecha BETWEEN :desde AND :hasta " +
	       "ORDER BY m.fecha DESC")
	List<MovimientoDTO> findMovimientosCajaLiteEntreFechas(@Param("cajaId") Integer cajaId,
	        @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
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

    /** Movimientos del proveedor (destino u origen) acotados a un rango de fechas — para el resumen del día. */
    @Query("SELECT m FROM Movimiento m WHERE (m.pagoProveedor.id = :provId OR m.proveedorOrigen.id = :provId) " +
           "AND m.fecha >= :desde AND m.fecha < :hasta ORDER BY m.fecha DESC")
    List<Movimiento> findMovimientosProveedorEntreFechas(@Param("provId") Integer provId,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
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
    List<Movimiento> findByCaja_IdAndTipoAndFechaBetweenOrderByFechaDesc(
            Integer cajaId,
            String tipo,
            LocalDateTime desde,
            LocalDateTime hasta
    );
    List<Movimiento> findByAjusteCuentaCop_IdAndFechaBetween(
            Integer cuentaId,
            LocalDateTime desde,
            LocalDateTime hasta
    );

    // ── Histórico de caja: recalculo en cascada al editar/eliminar ──

    /** Movimientos de esta caja (como origen) que pasaron DESPUÉS de la fecha dada — para propagar el delta hacia adelante. */
    List<Movimiento> findByCaja_IdAndFechaAfterOrderByFechaAsc(Integer cajaId, LocalDateTime fecha);

    /** Igual, pero para movimientos donde esta caja es la DESTINO (ej. TRANSFERENCIA CAJA). */
    List<Movimiento> findByCajaDestino_IdAndFechaAfterOrderByFechaAsc(Integer cajaId, LocalDateTime fecha);

    /** Todos los movimientos de una caja (origen o destino), del más viejo al más nuevo — para el backfill inicial. */
    List<Movimiento> findByCaja_IdOrCajaDestino_IdOrderByFechaAsc(Integer cajaId1, Integer cajaId2);

    /**
     * Último movimiento de una caja (como origen o destino) ANTES de la fecha dada,
     * el más reciente primero. Se usa Pageable en vez de un derivado "findFirstBy...Or...And..."
     * porque la generación de queries de Spring Data mezcla mal el OR y el AND en un
     * mismo nombre de método (quedaría "caja=? OR (cajaDestino=? AND fecha<?)" en vez
     * de "(caja=? OR cajaDestino=?) AND fecha<?"). Con @Query se controla exacto.
     * Se usa para saber con qué saldo cerró la caja el día (o sesión) anterior.
     */
    @Query("SELECT m FROM Movimiento m WHERE (m.caja.id = :cajaId OR m.cajaDestino.id = :cajaId) " +
           "AND m.fecha < :fecha ORDER BY m.fecha DESC")
    List<Movimiento> findUltimoMovimientoDeCajaAntesDe(@Param("cajaId") Integer cajaId,
            @Param("fecha") LocalDateTime fecha, Pageable pageable);

}
