package com.binance.web.BinanceAPI;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Repository.BuyDollarsRepository;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TronScanController {

    @Autowired
    private TronScanService tronScanService;

    @Autowired
    private BuyDollarsRepository buyDollarsRepository;

    @GetMapping("/trx-entradas")
    public ResponseEntity<List<BuyDollarsDto>> getTrustTransactions() {
        String walletAddress = "TJjK97sE4anv35hBQGwGEZEo5FYxToxFQM";

        // Obtener IDs ya registrados como compras
        Set<String> assignedIds = buyDollarsRepository.findAll().stream()
                .map(BuyDollars::getIdDeposit)
                .collect(Collectors.toSet());
        // Obtener y procesar las transacciones
        String response = tronScanService.getTransactions(walletAddress);
        List<BuyDollarsDto> transactions = tronScanService.parseIncomingTransactions(response, walletAddress, assignedIds);

        return ResponseEntity.ok(transactions);
    }
}

