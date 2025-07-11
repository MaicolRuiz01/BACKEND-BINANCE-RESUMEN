package com.binance.web.transacciones;

import java.util.List;

import com.binance.web.Entity.Transacciones;

public interface TransaccionesService {

	Transacciones guardarTransaccion(TransaccionesDTO dto) throws IllegalArgumentException;
	List<TransaccionesDTO> saveAndFetchTodayTraspasos();
}
