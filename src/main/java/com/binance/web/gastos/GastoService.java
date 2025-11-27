package com.binance.web.gastos;

import com.binance.web.Entity.Gasto;

public interface GastoService {
	
	Gasto saveGasto(Gasto nuevoGasto);

	Double totalGastosHoyCaja(Integer cajaId);

	Double totalGastosHoyCuentaCop(Integer cuentaId);

}
