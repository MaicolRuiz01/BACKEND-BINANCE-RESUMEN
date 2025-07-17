package com.binance.web.movimientos;


import java.util.List;

import com.binance.web.Entity.Movimiento;

public interface MovimientoService {
	
	Movimiento RegistrarTransferencia(Integer idCuentoFrom, Integer idCuentaTo, Double monto);
	Movimiento RegistrarRetiro(Integer cuentaId, Double monto);
	Movimiento RegistrarDeposito(Integer cuentaId, Double monto);
	List<Movimiento> listar();
}
