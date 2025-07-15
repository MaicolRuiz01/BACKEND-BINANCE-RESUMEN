package com.binance.web.averageRate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("tasa-promedio")
public class AverageRateController {
	
	@Autowired
	private AverageRateService averageRateService;
	
	@GetMapping()
	private ResponseEntity<Double> obtenerUltimaTasaPromedio(){
		Double tasaPromedio = averageRateService.getUltimaTasaPromedio().getAverageRate();
		return ResponseEntity.ok(tasaPromedio);	
		}

}
