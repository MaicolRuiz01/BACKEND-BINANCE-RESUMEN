package com.binance.web.movimientos;


import java.util.List;

import com.binance.web.Entity.Movimiento;

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
}
