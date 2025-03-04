package com.binance.web.controller;

import com.binance.web.service.BinanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PaymentController {

    private final BinanceService binanceService;

    public PaymentController(BinanceService binanceService) {
        this.binanceService = binanceService;
    }

    @GetMapping("/payments")
    public ResponseEntity<String> getPaymentHistory(@RequestParam String account) {
        String response = binanceService.getPaymentHistory(account);
        return ResponseEntity.ok().body(response);
    }
}
