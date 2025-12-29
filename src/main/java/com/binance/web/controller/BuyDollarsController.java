package com.binance.web.controller;

import java.util.List;
import java.util.stream.Collectors;

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
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.model.BuyDollarsDto;
import com.binance.web.service.BuyDollarsService;

@RestController
@RequestMapping("/api/buy-dollars")
public class BuyDollarsController {

    @Autowired
    private BuyDollarsService buyDollarsService;

    @Autowired
    private AccountBinanceRepository accountBinanceRepository; // Inyección del repositorio

    @PostMapping
    public ResponseEntity<BuyDollars> createBuyDollars(@RequestBody BuyDollarsDto buyDollarsDto) {
        // Buscar la cuenta de Binance que coincida con el nombre proporcionado
        AccountBinance accountBinance = accountBinanceRepository.findByName(buyDollarsDto.getNameAccount());

        if (accountBinance == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null); // Si no se encuentra la cuenta
        }

        // Asignar la cuenta de Binance al DTO de compra de dólares
        buyDollarsDto.setAccountBinanceId(accountBinance.getId());

        // Crear la compra de dólares
        BuyDollars newBuy = buyDollarsService.createBuyDollars(buyDollarsDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(newBuy);
    }

    @GetMapping
    public ResponseEntity<List<BuyDollars>> getAllBuyDollars() {
        List<BuyDollars> compras = buyDollarsService.getAllBuyDollars();
        return ResponseEntity.ok(compras);
    }

    @GetMapping("/listado")
    public ResponseEntity<List<BuyDollarsDto>> getBuyDollarsList() {
        List<BuyDollarsDto> dtos = buyDollarsService.getAllBuyDollars().stream()
            .filter(b -> Boolean.TRUE.equals(b.getAsignada())) // ✅ solo asignadas
            .map(buy -> {
                BuyDollarsDto dto = new BuyDollarsDto();
                dto.setId(buy.getId());
                dto.setTasa(buy.getTasa());
                dto.setNameAccount(buy.getNameAccount());
                dto.setDate(buy.getDate());
                dto.setIdDeposit(buy.getIdDeposit());
                dto.setPesos(buy.getPesos());
                dto.setAccountBinanceId(buy.getAccountBinance() != null ? buy.getAccountBinance().getId() : null);
                dto.setSupplierId(buy.getSupplier() != null ? buy.getSupplier().getId() : null);
                // si tu DTO/Entidad lo tienen:
                dto.setCryptoSymbol(buy.getCryptoSymbol());
                dto.setAmount(buy.getAmount());
                return dto;
            })
            .toList();

        return ResponseEntity.ok(dtos);
    }


    @PutMapping("/{id}")
    public ResponseEntity<BuyDollars> updateBuyDollars(@PathVariable Integer id, @RequestBody BuyDollarsDto dto) {
        try {
            BuyDollars updated = buyDollarsService.updateBuyDollars(id, dto);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @PostMapping("/importar-automatico")
    public ResponseEntity<Void> importarComprasAutomaticamente() {
        buyDollarsService.registrarComprasAutomaticamente();
        return ResponseEntity.ok().build();
    }

    // En BuyDollarsController.java
    @GetMapping("/no-asignadas-hoy")
    public ResponseEntity<List<BuyDollarsDto>> getNoAsignadasHoy() {
        return ResponseEntity.ok(buyDollarsService.getComprasNoAsignadasHoy());
    }

    @PutMapping("/asignar/{id}")
    public ResponseEntity<?> asignarCompra(
            @PathVariable Integer id,
            @RequestBody BuyDollarsDto dto) {
        try {
            BuyDollars updated = buyDollarsService.asignarCompra(id, dto);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
