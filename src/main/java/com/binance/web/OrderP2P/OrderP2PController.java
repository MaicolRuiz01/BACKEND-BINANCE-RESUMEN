package com.binance.web.OrderP2P;

import java.util.Date;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
	    public ResponseEntity<List<OrderP2PDto>> getP2POrders(@RequestParam String account) {
	    	  List<OrderP2PDto> ordenes = orderP2PService.showOrderP2PToday(account);
	    	  return ResponseEntity.ok(ordenes);
	    }
	    
	    @GetMapping("/date-range")
	    public ResponseEntity<List<OrderP2PDto>> getOrdersByDateRange(
	            @RequestParam String account,
	            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date fechaInicio,
	            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date fechaFin) {

	        List<OrderP2PDto> ordenes = orderP2PService.showOrderP2PByDateRange(account, fechaInicio, fechaFin);
	        return ResponseEntity.ok(ordenes);
	    }
}
