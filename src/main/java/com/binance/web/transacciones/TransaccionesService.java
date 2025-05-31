package com.binance.web.transacciones;

import com.binance.web.Entity.Transacciones;

public interface TransaccionesService {

	Transacciones guardarTransaccion(TransaccionesDTO dto) throws IllegalArgumentException;
}
