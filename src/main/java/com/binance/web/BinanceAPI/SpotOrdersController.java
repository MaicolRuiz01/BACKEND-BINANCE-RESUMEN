package com.binance.web.BinanceAPI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spot-orders")
public class SpotOrdersController {

    @Autowired
    private BinanceService binanceService;

    @GetMapping
    public ResponseEntity<String> getSpotOrders(@RequestParam String account,
                                                @RequestParam String symbol,
                                                @RequestParam(defaultValue = "100") int limit) {
        String response = binanceService.getSpotOrders(account, symbol, limit);
        return ResponseEntity.ok(response);
    }
}

