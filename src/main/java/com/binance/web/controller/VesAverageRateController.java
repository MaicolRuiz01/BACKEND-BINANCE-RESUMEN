package com.binance.web.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.VesAverageRate;
import com.binance.web.service.VesAverageRateService;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ves-average-rate")
@RequiredArgsConstructor
public class VesAverageRateController {

    private final VesAverageRateService vesAverageRateService;

    @Data
    public static class InitVesRateRequest {
        private Double tasaInicialCopPorVes;
    }

    @PostMapping("/inicializar")
    public ResponseEntity<VesAverageRate> inicializar(@RequestBody InitVesRateRequest req) {
        if (req.getTasaInicialCopPorVes() == null || req.getTasaInicialCopPorVes() <= 0) {
            return ResponseEntity.badRequest().build();
        }
        VesAverageRate rate = vesAverageRateService.inicializarTasaInicial(
                req.getTasaInicialCopPorVes(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(rate);
    }
}
