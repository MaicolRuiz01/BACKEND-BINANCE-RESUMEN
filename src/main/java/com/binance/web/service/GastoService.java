package com.binance.web.service;

import com.binance.web.Entity.Gasto;

public interface GastoService {
	
	Gasto saveGasto(Gasto nuevoGasto);

	/** Elimina el gasto y DEVUELVE el saldo restado a la cuenta COP o caja afectada. */
	void eliminarGasto(Integer id);

	Double totalGastosHoyCaja(Integer cajaId);

	Double totalGastosHoyCuentaCop(Integer cuentaId);

}
