package com.binance.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.VesRatesConfig;
import com.binance.web.model.VesRatesConfigDto;
import com.binance.web.service.VesRatesConfigService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ves-config")
@RequiredArgsConstructor
public class VesRatesConfigController {

    private final VesRatesConfigService service;

    @GetMapping
    public ResponseEntity<VesRatesConfig> get() {
        return ResponseEntity.ok(service.getConfig());
    }

    @PutMapping
    public ResponseEntity<VesRatesConfig> save(@RequestBody VesRatesConfigDto dto) {
        return ResponseEntity.ok(service.saveConfig(dto));
    }
}

