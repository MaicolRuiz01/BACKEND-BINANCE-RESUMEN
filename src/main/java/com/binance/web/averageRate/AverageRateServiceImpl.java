package com.binance.web.averageRate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Repository.AverageRateRepository;

@Service
public class AverageRateServiceImpl implements AverageRateService{
	
	@Autowired
	private AverageRateRepository averageRateRepository;
	
	private AccountBinanceService accountBinanceService;

	@Override
	public AverageRate getUltimaTasaPromedio() {
		return averageRateRepository.findTopByOrderByIdDesc().orElse(null);
	}

	@Override
	public AverageRate guardarNuevaTasa(Double nuevaTasa, Double nuevoSaldo) {
		
		AverageRate tasaPromedio = new AverageRate();
		tasaPromedio.setAverageRate(nuevaTasa);
		tasaPromedio.setSaldoTotalInterno(nuevoSaldo);
		tasaPromedio.setFecha(LocalDateTime.now());
		return averageRateRepository.save(tasaPromedio);
	}

}
