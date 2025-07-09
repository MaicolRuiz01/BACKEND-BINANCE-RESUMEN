package com.binance.web.balance.saleP2P;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.balance.PurchaseRate.PurchaseRateService;


@RestController
@RequestMapping("/balance/saleP2P")
public class BalanceSaleP2PController {
	
	@Autowired
	private BalanceSaleP2PService balanceSaleP2PService;

	 
	@GetMapping
	public ResponseEntity<BalanceSaleP2PDto> getBalanceSaleP2P(
	        @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
	    BalanceSaleP2PDto balanceSaleP2P = balanceSaleP2PService.balanceSaleP2PDay(fecha);
	    return ResponseEntity.ok(balanceSaleP2P);
	}
	
}
