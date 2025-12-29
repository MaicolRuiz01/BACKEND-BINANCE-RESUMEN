package com.binance.web.service;

import java.time.LocalDateTime;

import com.binance.web.Entity.AverageRate;

public interface AverageRateService {
	AverageRate getUltimaTasaPromedio(); // puede ser null
    AverageRate guardarNuevaTasa(Double nuevaTasa, Double nuevoSaldo,  LocalDateTime fecha);
	AverageRate getTasaPorDia(LocalDateTime fecha);
	AverageRate actualizarTasaPromedioPorCompra(LocalDateTime fechaCompra, Double montoUsdtCompra, Double tasaCompra);
	AverageRate inicializarTasaPromedioInicial(Double tasaInicial, LocalDateTime fecha);

}
