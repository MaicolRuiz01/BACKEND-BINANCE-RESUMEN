package com.binance.web.SellDollars;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sell-dollars")
@RequiredArgsConstructor
public class SellDollarsController {
	
	private final SellDollarsService service;

    @PostMapping
    public ResponseEntity<SellDollars> create(@RequestBody SellDollarsDto dto) {
        SellDollars created = service.createSellDollars(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

}
