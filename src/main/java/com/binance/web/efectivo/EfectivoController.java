package com.binance.web.efectivo;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

}
