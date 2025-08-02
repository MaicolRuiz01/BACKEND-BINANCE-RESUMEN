package com.binance.web.SellDollars;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Repository.AccountBinanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;
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
    	System.out.println("Datos recibidos: " + sellDollarsDto);
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
    
    
    
    @GetMapping("/no-asignadas")
    public ResponseEntity<List<SellDollars>> getVentasNoAsignadas() {
        List<SellDollars> ventas = service.registrarYObtenerVentasNoAsignadas();
        return ResponseEntity.ok(ventas);
    }
    
    @GetMapping("/listar-dto")
    public ResponseEntity<List<SellDollarsDto>> listarVentasDto() {
        return ResponseEntity.ok(service.listarVentasDto());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody SellDollarsDto dto) {
        try {
            System.out.println("💾 Recibido para actualizar venta ID " + id + ": " + new ObjectMapper().writeValueAsString(dto));

            // Validar campos obligatorios manualmente
            if (dto.getTasa() == null || dto.getDollars() == null || dto.getPesos() == null) {
                return ResponseEntity.badRequest().body("Faltan campos obligatorios: tasa, dollars o pesos.");
            }

            // Continuar con la actualización
            SellDollars updated = service.updateSellDollars(id, dto);
            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            e.printStackTrace(); // Imprimir la excepción real
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error al actualizar la venta: " + e.getMessage());
        }
    }

}