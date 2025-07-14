package com.binance.web.balance.PurchaseRate;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.binance.web.Entity.PurchaseRate;
import com.binance.web.Repository.PurchaseRateRepository;

@RestController
@RequestMapping("/purchase-rate")
public class PurchaseRateController {
	
	private final PurchaseRateRepository purchaseRateRepository;

    public PurchaseRateController(PurchaseRateRepository purchaseRateRepository) {
        this.purchaseRateRepository = purchaseRateRepository;
    }

    /** 
     * Devuelve la tasa (rate) más reciente de la tabla purchase_rate.
     */
    @GetMapping("/latest")
    public ResponseEntity<Double> getLatestRate() {
        PurchaseRate latest = purchaseRateRepository.findTopByOrderByDateDesc();
        return latest != null ? ResponseEntity.ok(latest.getRate()) : ResponseEntity.noContent().build();
    }


}
