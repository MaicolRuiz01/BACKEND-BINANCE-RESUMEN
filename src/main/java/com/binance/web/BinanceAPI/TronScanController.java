package com.binance.web.BinanceAPI;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.Transacciones;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.SellDollars.SellDollarsDto;
import com.binance.web.transacciones.TransaccionesDTO;
import com.binance.web.transacciones.TransaccionesRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    @Autowired
    private TransaccionesRepository transaccionesRepository;
    @Autowired
    private AccountBinanceRepository accountBinanceRepository;

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
        String walletAddress = "TPDNfJ72Fh6Hrfk6faYVps1rN78NB8LQGu";  // O recibir por parámetro
        String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
        return ResponseEntity.ok(response);
    }
    
    
    @GetMapping("/usdt-entradas")
    public ResponseEntity<List<BuyDollarsDto>> getUSDTIncomingTransfers() {
        String walletAddress = "TPDNfJ72Fh6Hrfk6faYVps1rN78NB8LQGu";
        Set<String> assignedIds = buyDollarsRepository.findAll().stream()
                .map(BuyDollars::getIdDeposit)
                .collect(Collectors.toSet());

        String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
        List<BuyDollarsDto> incoming = tronScanService.parseTRC20IncomingUSDTTransfers(response, walletAddress, assignedIds);
        return ResponseEntity.ok(incoming);
    }

    @GetMapping("/usdt-salidas")
    public ResponseEntity<List<SellDollarsDto>> getUSDTOutgoingTransfers() {
        String walletAddress = "TPDNfJ72Fh6Hrfk6faYVps1rN78NB8LQGu";
        Set<String> assignedIds = sellDollarsRepository.findAll().stream()
                .map(SellDollars::getIdWithdrawals)  // Ajusta según nombre campo ID
                .collect(Collectors.toSet());

        String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
        List<SellDollarsDto> outgoing = tronScanService.parseTRC20OutgoingUSDTTransfers(response, walletAddress, assignedIds);
        return ResponseEntity.ok(outgoing);
    }
    
    
    @GetMapping("/trust-transacciones-salientes")
    public ResponseEntity<List<TransaccionesDTO>> getTrustOutgoingTransfers() {
        String walletAddress = "TPDNfJ72Fh6Hrfk6faYVps1rN78NB8LQGu";

        // IDs ya registrados en transacciones
        Set<String> registeredIds = transaccionesRepository.findAll().stream()
                .map(Transacciones::getIdtransaccion)
                .collect(Collectors.toSet());

        // Direcciones registradas en la base de datos
        Set<String> registeredAddresses = accountBinanceRepository.findAll().stream()
                .map(AccountBinance::getAddress)
                .filter(address -> address != null && !address.isBlank())
                .collect(Collectors.toSet());

        // Obtener datos desde la API de TronGrid
        String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);

        List<TransaccionesDTO> result = new ArrayList<>();
        try {
            JsonNode root = new ObjectMapper().readTree(response);
            JsonNode data = root.path("data");

            if (data.isArray()) {
                for (JsonNode tx : data) {
                    String from = tx.path("from").asText();
                    String to = tx.path("to").asText();
                    String txId = tx.path("transaction_id").asText();
                    JsonNode tokenInfo = tx.path("token_info");
                    String symbol = tokenInfo.path("symbol").asText();

                    // Validar: salida de nuestra wallet, moneda USDT, no duplicada, y destino registrado
                    if (from.equalsIgnoreCase(walletAddress)
                            && symbol.equalsIgnoreCase("USDT")
                            && !registeredIds.contains(txId)
                            && registeredAddresses.contains(to)) {

                        double amount = Double.parseDouble(tx.path("value").asText("0")) / 1_000_000.0;
                        long timestamp = tx.path("block_timestamp").asLong();

                        TransaccionesDTO dto = new TransaccionesDTO();
                        dto.setIdtransaccion(txId);
                        dto.setCuentaFrom("TRUST");
                        dto.setCuentaTo(to);
                        dto.setFecha(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota")));
                        dto.setMonto(amount);
                        dto.setTipo("TRUST");

                        result.add(dto);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(new ArrayList<>());
        }

        return ResponseEntity.ok(result);
    }
    
    //OBTIENE EL SALDO DE LA BILLETERA EN USDT
    @GetMapping("/wallet-total-assets")
    public ResponseEntity<Double> getWalletTotalAssets() {
        String walletAddress = "TPDNfJ72Fh6Hrfk6faYVps1rN78NB8LQGu";
        double totalUsd = tronScanService.getTotalAssetTokenOverview(walletAddress);
        return ResponseEntity.ok(totalUsd);
    }

}

