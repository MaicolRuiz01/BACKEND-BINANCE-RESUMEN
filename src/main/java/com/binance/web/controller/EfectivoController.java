package com.binance.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.Efectivo;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.service.EfectivoService;

@RestController
@RequestMapping("/efectivo")
public class EfectivoController {
	
	@Autowired
	private EfectivoRepository efectivoRepository;
	@Autowired
	private EfectivoService efectivoService;
	
	@GetMapping
	public List<Efectivo> obtenerEfectivo(){
		return efectivoRepository.findAll();
	}
	
	@PostMapping
    public ResponseEntity<Efectivo> guardarCaja(@RequestBody Efectivo caja) {
        Efectivo nueva = efectivoService.crearCaja(caja);
        return ResponseEntity.status(HttpStatus.CREATED).body(nueva);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarCaja(@PathVariable Integer id) {
        if (!efectivoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            efectivoService.eliminarCaja(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "La caja tiene movimientos o gastos asociados y no se puede eliminar."));
        }
    }

}
