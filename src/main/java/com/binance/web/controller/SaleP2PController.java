package com.binance.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.entity.SaleP2P;
import com.binance.web.service.SaleP2PService;

@RestController
@RequestMapping("/saleP2P")
public class SaleP2PController {
	
	
    private final SaleP2PService saleP2PService;
	
	public SaleP2PController(SaleP2PService saleP2PService) {
	    this.saleP2PService = saleP2PService;
	}


    @GetMapping
    public ResponseEntity<List<SaleP2P>> getAllSales() {
        List<SaleP2P> sales = saleP2PService.findAllSaleP2P();
        return ResponseEntity.ok(sales);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleP2P> getSaleById(@PathVariable Integer id) {
        SaleP2P saleP2P = saleP2PService.findByIdSaleP2P(id);
        return saleP2P != null ? ResponseEntity.ok(saleP2P) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<SaleP2P> createSale(@RequestBody SaleP2P saleP2P) {
        saleP2PService.saveSaleP2P(saleP2P);
        return ResponseEntity.status(HttpStatus.CREATED).body(saleP2P);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SaleP2P> updateSale(@PathVariable Integer id, @RequestBody SaleP2P saleP2P) {
        SaleP2P existingSale = saleP2PService.findByIdSaleP2P(id);
        if (existingSale == null) {
            return ResponseEntity.notFound().build();
        }
        saleP2PService.updateSaleP2P(id, saleP2P);
        return ResponseEntity.ok(saleP2P);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSale(@PathVariable Integer id) {
        saleP2PService.deleteSaleP2P(id);
        return ResponseEntity.noContent().build();
    }
}

