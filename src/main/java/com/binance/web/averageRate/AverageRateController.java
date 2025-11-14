package com.binance.web.averageRate;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.AverageRate;

@RestController
@RequestMapping("tasa-promedio")
public class AverageRateController {
	
	@Autowired
	private AverageRateService averageRateService;
	
	@GetMapping("/ultima")
    public ResponseEntity<AverageRate> obtenerUltimaTasaPromedio() {
        AverageRate ultima = averageRateService.getUltimaTasaPromedio();
        if (ultima == null) {
            // Solo si realmente no hay ningún registro en la tabla
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ultima);
    }

    // 🔹 POST /api/average-rate/inicializar
    @PostMapping("/inicializar")
    public ResponseEntity<AverageRate> inicializar(@RequestBody InitialRateRequest request) {
        // Si YA hay una tasa, no deberíamos volver a inicializar
        AverageRate existente = averageRateService.getUltimaTasaPromedio();
        if (existente != null) {
            // 409 = conflicto (ya hay algo)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(existente);
        }

        AverageRate creada = averageRateService.inicializarTasaPromedioInicial(
                request.getTasaInicial(),
                LocalDateTime.now()
        );
        return ResponseEntity.ok(creada);
    }

    // DTO simple para el body del POST
    public static class InitialRateRequest {
        private Double tasaInicial;

        public Double getTasaInicial() {
            return tasaInicial;
        }

        public void setTasaInicial(Double tasaInicial) {
            this.tasaInicial = tasaInicial;
        }
    }

}
