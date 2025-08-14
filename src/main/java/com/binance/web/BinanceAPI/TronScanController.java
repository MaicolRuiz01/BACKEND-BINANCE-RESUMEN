package com.binance.web.BinanceAPI;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.Transacciones;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.ClienteRepository;
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
    @Autowired
    private ClienteRepository clienteRepository;
    
    private List<String> getAllTrustWallets() {
        return accountBinanceRepository.findAll().stream()
                .filter(account -> "TRUST".equalsIgnoreCase(account.getTipo()))
                .map(AccountBinance::getAddress)
                .filter(address -> address != null && !address.isBlank())
                .toList();
    }


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
        Set<String> assignedIds = buyDollarsRepository.findAll().stream()
                .map(BuyDollars::getIdDeposit)
                .collect(Collectors.toSet());

        List<AccountBinance> trustWallets = accountBinanceRepository.findAll().stream()
                .filter(account -> "TRUST".equalsIgnoreCase(account.getTipo()))
                .filter(account -> account.getAddress() != null && !account.getAddress().isBlank())
                .toList();

        List<BuyDollarsDto> result = new ArrayList<>();

        for (AccountBinance trustAccount : trustWallets) {
            String walletAddress = trustAccount.getAddress();
            String accountName = trustAccount.getName();
            String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
            List<BuyDollarsDto> incoming = tronScanService.parseTRC20IncomingUSDTTransfers(response, walletAddress, accountName, assignedIds);
            result.addAll(incoming);
        }

        return ResponseEntity.ok(result);
    }



    @GetMapping("/usdt-salidas")
    public ResponseEntity<List<SellDollarsDto>> getUSDTOutgoingTransfers() {
        Set<String> assignedIds = sellDollarsRepository.findAll().stream()
                .map(SellDollars::getIdWithdrawals)
                .collect(Collectors.toSet());

        // 🔍 Crear mapa de wallets registradas -> cliente
        Map<String, Cliente> clientePorWallet = clienteRepository.findAll().stream()
                .filter(c -> c.getWallet() != null && !c.getWallet().isBlank())
                .collect(Collectors.toMap(
                    c -> c.getWallet().trim().toLowerCase(),
                    Function.identity()
                ));

        // 🔍 Filtrar solo cuentas tipo TRUST
        List<AccountBinance> trustWallets = accountBinanceRepository.findAll().stream()
                .filter(account -> "TRUST".equalsIgnoreCase(account.getTipo()))
                .filter(account -> account.getAddress() != null && !account.getAddress().isBlank())
                .toList();

        List<SellDollarsDto> result = new ArrayList<>();

        for (AccountBinance trustAccount : trustWallets) {
            String walletAddress = trustAccount.getAddress();
            String accountName = trustAccount.getName();
            String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);

            // 📌 Ahora pasamos el mapa de clientes al parser
            List<SellDollarsDto> outgoing = tronScanService.parseTRC20OutgoingUSDTTransfers(
                    response, walletAddress, accountName, assignedIds, clientePorWallet
            );

            result.addAll(outgoing);
        }

        return ResponseEntity.ok(result);
    }


    
    
    @GetMapping("/trust-transacciones-salientes")
    public ResponseEntity<List<TransaccionesDTO>> getTrustOutgoingTransfers() {

        // 1️⃣ IDs ya registrados para evitar duplicados
        Set<String> registeredIds = transaccionesRepository.findAll().stream()
                .map(Transacciones::getIdtransaccion)
                .collect(Collectors.toSet());

        // 2️⃣ Direcciones de destino válidas (pueden ser TRUST o BINANCE)
        Set<String> registeredAddresses = accountBinanceRepository.findAll().stream()
                .map(AccountBinance::getAddress)
                .filter(address -> address != null && !address.isBlank())
                .collect(Collectors.toSet());

        // 3️⃣ Traer todas las wallets tipo TRUST activas en la DB
        List<String> trustWallets = accountBinanceRepository.findAll().stream()
                .filter(a -> "TRUST".equalsIgnoreCase(a.getTipo()))
                .map(AccountBinance::getAddress)
                .filter(address -> address != null && !address.isBlank())
                .toList();

        List<TransaccionesDTO> result = new ArrayList<>();

        for (String walletAddress : trustWallets) {
            try {
                String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
                JsonNode root = new ObjectMapper().readTree(response);
                JsonNode data = root.path("data");

                if (data.isArray()) {
                    for (JsonNode tx : data) {
                        String from = tx.path("from").asText();
                        String to = tx.path("to").asText();
                        String txId = tx.path("transaction_id").asText();
                        JsonNode tokenInfo = tx.path("token_info");
                        String symbol = tokenInfo.path("symbol").asText();

                        if (from.equalsIgnoreCase(walletAddress)
                                && symbol.equalsIgnoreCase("USDT")
                                && !registeredIds.contains(txId)
                                && registeredAddresses.contains(to)) {

                            double amount = Double.parseDouble(tx.path("value").asText("0")) / 1_000_000.0;
                            long timestamp = tx.path("block_timestamp").asLong();

                            TransaccionesDTO dto = new TransaccionesDTO();
                            dto.setIdtransaccion(txId);
                            dto.setCuentaFrom(walletAddress);
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
            }
        }

        return ResponseEntity.ok(result);
    }

    //OBTIENE EL SALDO DE LA BILLETERA EN USDT
    @GetMapping("/wallet-total-assets")
    public ResponseEntity<Double> getWalletTotalAssets(@RequestParam String walletAddress) {
        double totalUsd = tronScanService.getTotalAssetTokenOverview(walletAddress);
        return ResponseEntity.ok(totalUsd);
    }


}

