package com.binance.web.controller;



import com.binance.web.service.BinanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/p2p")
@CrossOrigin(origins = "*")
public class P2PController {

    private final BinanceService binanceService;

    public P2PController(BinanceService binanceService) {
        this.binanceService = binanceService;
    }

    @GetMapping("/orders")
    public ResponseEntity<String> getP2POrders(@RequestParam String account) {
        return ResponseEntity.ok().body(binanceService.getP2POrders(account));
    }

}

