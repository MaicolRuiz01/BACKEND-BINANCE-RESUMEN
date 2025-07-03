package com.binance.web.SellDollars;

import java.time.LocalDateTime;
import java.util.List;

import com.binance.web.Entity.SellDollars;

public interface SellDollarsService {
	SellDollars createSellDollars(SellDollarsDto dto);
	List<SellDollars> registrarYObtenerVentasNoAsignadas();
	List<SellDollars> obtenerVentasEntreFechas(LocalDateTime inicio, LocalDateTime fin);
	void saveUtilityDefinitive(List<SellDollars> rangoSell, Double averageRate);

}
