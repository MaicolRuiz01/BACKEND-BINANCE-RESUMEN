package com.binance.web.controller;

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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.model.AssignAccountDto;
import com.binance.web.model.SellDollarsDto;
import com.binance.web.service.SellDollarsService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/sell-dollars")
@RequiredArgsConstructor
public class SellDollarsController {
    
    private final SellDollarsService service;
    
    
    private final SellDollarsRepository sellDollarsRepository;

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
        //service.registrarVentasAutomaticamente();   // 👈 importa todo antes de listar
        return ResponseEntity.ok(service.registrarYObtenerVentasNoAsignadas());
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
    
    @PostMapping("/importar-automatico")
    public ResponseEntity<Void> importarVentasAutomaticamente() {
        service.registrarVentasAutomaticamente();
        return ResponseEntity.ok().build();
    }

    /** Importación manual mirando {dias} días atrás (0 = solo hoy). Solo para probar/reprocesar
     *  retiros de Bybit de días anteriores. Acepta GET y POST para poder llamarlo desde el
     *  navegador. Ej: /sell-dollars/importar-bybit?dias=1 */
    @RequestMapping(value = "/importar-bybit", method = { RequestMethod.GET, RequestMethod.POST })
    public ResponseEntity<Void> importarBybitDiasAtras(@RequestParam(defaultValue = "1") int dias) {
        service.registrarVentasAutomaticamente(dias);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/asignar/{id}")
    public ResponseEntity<?> asignar(@PathVariable Integer id, @RequestBody SellDollarsDto dto) {
        // Antes iba sin try/catch: cualquier fallo salía como un 500 opaco y el front
        // mostraba "error" sin decir qué pasó. Ahora se devuelve el motivo real.
        try {
            return ResponseEntity.ok(service.asignarVenta(id, dto));
        } catch (RuntimeException e) {
            log.error("[asignarVenta] Error asignando venta {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Error al asignar la venta"));
        }
    }
    
    @PutMapping("/asignar-solana/{id}")
    public ResponseEntity<SellDollars> asignarVentaSolana(
        @PathVariable Integer id,
        @RequestBody Map<String, List<AssignAccountDto>> body
    ) {
        SellDollarsDto dto = new SellDollarsDto();
        dto.setAccounts(body.get("accounts"));
        return ResponseEntity.ok(service.asignarVenta(id, dto));
    }

}