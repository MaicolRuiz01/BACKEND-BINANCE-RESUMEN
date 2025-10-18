package com.binance.web.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.Movimiento;

public interface MovimientoRepository extends JpaRepository<Movimiento, Integer> {
	
	List<Movimiento> findByTipo(String tipo);
	List<Movimiento> findByTipoAndPagoProveedor_Id(String tipo, Integer proveedorId);
	List<Movimiento> findByTipoAndPagoCliente_Id(String tipo, Integer clienteId);
    List<Movimiento> findByCuentaOrigenIdOrCuentaDestinoId(Integer cuentaOrigenId, Integer cuentaDestinoId);



}
