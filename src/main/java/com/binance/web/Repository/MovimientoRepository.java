package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.Movimiento;

@Repository
public interface MovimientoRepository extends JpaRepository<Movimiento, Integer> {
	
	List<Movimiento> findByTipo(String tipo);
	List<Movimiento> findByTipoAndPagoProveedor_Id(String tipo, Integer proveedorId);
	List<Movimiento> findByTipoAndPagoCliente_Id(String tipo, Integer clienteId);
    List<Movimiento> findByCuentaOrigenIdOrCuentaDestinoId(Integer cuentaOrigenId, Integer cuentaDestinoId);

	List<Movimiento> findByPagoCliente_Id(Integer clienteId);
	List<Movimiento> findByPagoCliente_IdOrClienteOrigen_IdOrderByFechaDesc(Integer clienteId1, Integer clienteId2);
	List<Movimiento> findByCaja_IdOrderByFechaDesc(Integer cajaId);
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
}
