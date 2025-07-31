package com.binance.web.efectivo;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.Efectivo;
import com.binance.web.Repository.EfectivoRepository;

@RestController
@RequestMapping("/efectivo")
public class EfectivoController {
	
	@Autowired
	private EfectivoRepository efectivoRepository;
	
	@GetMapping
	public List<Efectivo> obtenerEfectivo(){
		return efectivoRepository.findAll();
	}
	
	@PostMapping
	public ResponseEntity<Efectivo> guardarCaja(@RequestBody Efectivo caja) {
		efectivoRepository.save(caja);
		return ResponseEntity.status(HttpStatus.CREATED).body(caja);
	}

}
