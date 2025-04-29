package com.binance.web.BuyDollars;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.AccountBinance.AccountBinance;
import com.binance.web.AccountBinance.AccountBinanceRepository;

@RestController
@RequestMapping("/api/buy-dollars")
public class BuyDollarsController {
    
    @Autowired
    private BuyDollarsService buyDollarsService;

    @Autowired
    private AccountBinanceRepository accountBinanceRepository;  // Inyección del repositorio

    @PostMapping
    public ResponseEntity<BuyDollars> createBuyDollars(@RequestBody BuyDollarsDto buyDollarsDto) {
        // Buscar la cuenta de Binance que coincida con el nombre proporcionado
        AccountBinance accountBinance = accountBinanceRepository.findByName(buyDollarsDto.getNameAccount());
        
        if (accountBinance == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);  // Si no se encuentra la cuenta
        }

        // Asignar la cuenta de Binance al DTO de compra de dólares
        buyDollarsDto.setAccountBinanceId(accountBinance.getId());

        // Crear la compra de dólares
        BuyDollars newBuy = buyDollarsService.createBuyDollars(buyDollarsDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(newBuy);
    }
}
