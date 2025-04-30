package com.binance.web.SellDollars;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.AccountBinance.AccountBinanceRepository;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.SellDollars;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sell-dollars")
@RequiredArgsConstructor
public class SellDollarsController {
    
    private final SellDollarsService service;

    @Autowired
    private AccountBinanceRepository accountBinanceRepository;  // Inyección del repositorio

    @PostMapping
    public ResponseEntity<SellDollars> create(@RequestBody SellDollarsDto sellDollarsDto) {
        // Buscar la cuenta de Binance que coincida con el nombre proporcionado
        AccountBinance accountBinance = accountBinanceRepository.findByName(sellDollarsDto.getNameAccount());
        
        if (accountBinance == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);  // Si no se encuentra la cuenta
        }

        // Asignar la cuenta de Binance al DTO de venta de dólares
        sellDollarsDto.setAccountBinanceId(accountBinance.getId());

        // Crear la venta de dólares
        SellDollars created = service.createSellDollars(sellDollarsDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}