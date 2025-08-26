package com.binance.web.averageRate;

import java.time.LocalDateTime;

import com.binance.web.Entity.AverageRate;

public interface AverageRateService {
	AverageRate getUltimaTasaPromedio(); // puede ser null
    AverageRate guardarNuevaTasa(Double nuevaTasa, Double nuevoSaldo,  LocalDateTime fecha);

}
