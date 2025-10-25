package com.binance.web.movimientos;


import java.util.List;

import com.binance.web.Entity.Movimiento;
import com.binance.web.model.PagoClienteAClienteDto;

public interface MovimientoService {
	
	Movimiento RegistrarTransferencia(Integer idCuentoFrom, Integer idCuentaTo, Double monto);
	Movimiento RegistrarRetiro(Integer cuentaId, Integer cajaId, Double monto);
	Movimiento RegistrarDeposito(Integer cuentaId,Integer cajaId, Double monto);
	Movimiento registrarPagoCliente(Integer cuentaId, Integer clienteId, Double monto);
	List<Movimiento> listar();
	List<Movimiento> listarRetiros();
	List<Movimiento> listarDepositos();
	List<Movimiento> listarTransferencias();
	List<Movimiento> listarPagos();
	Movimiento actualizarMovimiento(Integer id, Double monto, Integer cuentaOrigenId, Integer cuentaDestinoId, Integer cajaId);
	Movimiento registrarPagoProveedor(Integer cuentaCopId, Integer cajaId, Integer proveedorOrigenId, Integer proveedorDestinoId,Integer clienteId, Double monto);
	List<Movimiento> listarPagosProveedorPorId(Integer proveedorId);
	List<Movimiento> listarMovimientosPorCliente(Integer clienteId);
	Movimiento registrarPagoCaja(Integer clienteId, Integer cajaId, Double monto);
	List<Movimiento> listarPagosCuentaPorId(Integer cajaId);
	Movimiento registrarPagoClienteACliente(PagoClienteAClienteDto dto);
	List<Movimiento> listarMovimientosPorCaja(Integer cajaId);
	


}
