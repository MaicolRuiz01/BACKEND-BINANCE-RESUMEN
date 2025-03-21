package com.binance.web.OrderP2P;

import java.util.List;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order-p2p")
public class OrderP2PController {

	 private final OrderP2PService orderP2PService;

	    public OrderP2PController(OrderP2PService orderP2PService) {
	        this.orderP2PService = orderP2PService;
	    }

	    @GetMapping
	    public List<OrderP2PDto> getP2POrders(@RequestParam String account) {
	        return orderP2PService.showOrderP2PToday(account);
	    }
}
