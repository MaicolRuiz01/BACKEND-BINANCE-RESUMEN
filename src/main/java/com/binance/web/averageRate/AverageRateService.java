package com.binance.web.averageRate;

import com.binance.web.Entity.AverageRate;

public interface AverageRateService {
	AverageRate getUltimaTasaPromedio(); // puede ser null
    AverageRate guardarNuevaTasa(Double nuevaTasa, Double nuevoSaldo);

}
