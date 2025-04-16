package com.binance.web.BinanceAPI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/futures")
public class FuturesController {
	
	@Autowired
    private BinanceService binanceService;

    // Endpoint para obtener órdenes de Futures
    @GetMapping("/orders")
    public String getFuturesOrders(@RequestParam String account,
                                   @RequestParam String symbol,
                                   @RequestParam int limit) {
        return binanceService.getFuturesOrders(account, symbol, limit);
    }

    // Endpoint para obtener posiciones de Futures
    @GetMapping("/positions")
    public String getFuturesPositions(@RequestParam String account) {
        return binanceService.getFuturesPositions(account);
    }

}
