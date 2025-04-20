package com.binance.web.BuyDollars;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/buy-dollars")
public class BuyDollarsController {
    @Autowired
    private BuyDollarsService buyDollarsService;

    // Inyección de dependencias vía constructor (mejor práctica que @Autowired en campo)
    public BuyDollarsController(BuyDollarsService buyDollarsService) {
        this.buyDollarsService = buyDollarsService;
    }

    @PostMapping
    public ResponseEntity<BuyDollars> createBuyDollars(@RequestBody BuyDollarsDto buyDollarsDto) {
        // Llamamos al servicio con el DTO para crear la entidad BuyDollars
        BuyDollars newBuy = buyDollarsService.createBuyDollars(buyDollarsDto);
        // Retornamos la entidad creada con código 201 (Created)
        return ResponseEntity.status(HttpStatus.CREATED).body(newBuy);
    }
}
