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
import com.binance.web.Entity.SellDollars;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.SellDollars.SellDollarsDto;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TronScanController {

    @Autowired
    private TronScanService tronScanService;

    @Autowired
    private BuyDollarsRepository buyDollarsRepository;

    @Autowired
    private SellDollarsRepository sellDollarsRepository;

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
    
    @GetMapping("/trx-salidas")
    public ResponseEntity<List<BuyDollarsDto>> getTrustOutgoingTransactions() {
        String walletAddress = "TJjK97sE4anv35hBQGwGEZEo5FYxToxFQM";

        // Obtener IDs ya registrados
        Set<String> assignedIds = buyDollarsRepository.findAll().stream()
                .map(BuyDollars::getIdDeposit)
                .collect(Collectors.toSet());

        String response = tronScanService.getTransactions(walletAddress);
        List<BuyDollarsDto> outgoingTransactions = tronScanService.parseOutgoingTransactions(response, walletAddress, assignedIds);

        return ResponseEntity.ok(outgoingTransactions);
    }
    

    
    //me las trae en bruto todas las usdt de trusWallet
    @GetMapping("/usdt-trc20-trongrid")
    public ResponseEntity<String> getTRC20TransfersUsingTronGrid() {
        String walletAddress = "TJjK97sE4anv35hBQGwGEZEo5FYxToxFQM";  // O recibir por parámetro
        String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
        return ResponseEntity.ok(response);
    }
    
    
    @GetMapping("/usdt-entradas")
    public ResponseEntity<List<BuyDollarsDto>> getUSDTIncomingTransfers() {
        String walletAddress = "TJjK97sE4anv35hBQGwGEZEo5FYxToxFQM";
        Set<String> assignedIds = buyDollarsRepository.findAll().stream()
                .map(BuyDollars::getIdDeposit)
                .collect(Collectors.toSet());

        String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
        List<BuyDollarsDto> incoming = tronScanService.parseTRC20IncomingUSDTTransfers(response, walletAddress, assignedIds);
        return ResponseEntity.ok(incoming);
    }

    @GetMapping("/usdt-salidas")
    public ResponseEntity<List<SellDollarsDto>> getUSDTOutgoingTransfers() {
        String walletAddress = "TJjK97sE4anv35hBQGwGEZEo5FYxToxFQM";
        Set<String> assignedIds = sellDollarsRepository.findAll().stream()
                .map(SellDollars::getIdWithdrawals)  // Ajusta según nombre campo ID
                .collect(Collectors.toSet());

        String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
        List<SellDollarsDto> outgoing = tronScanService.parseTRC20OutgoingUSDTTransfers(response, walletAddress, assignedIds);
        return ResponseEntity.ok(outgoing);
    }

}

