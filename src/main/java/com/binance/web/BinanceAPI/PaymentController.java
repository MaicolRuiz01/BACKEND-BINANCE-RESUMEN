package com.binance.web.BinanceAPI;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.AccountBinance.AccountBinanceRepository;
import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.BuyDollars.BuyDollarsService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.SellDollars.SellDollarsDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.binance.web.model.Transaction;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PaymentController {

    private final BinanceService binanceService;
    private final BuyDollarsService buyDollarsService;
    private final AccountBinanceRepository accountBinanceRepository; 

    public PaymentController(BinanceService binanceService, BuyDollarsService buyDollarsService, AccountBinanceRepository accountBinanceRepository) {
        this.binanceService = binanceService;
        this.buyDollarsService = buyDollarsService;
        this.accountBinanceRepository = accountBinanceRepository;
    }

    @GetMapping("/payments")
    public ResponseEntity<String> getPaymentHistory(@RequestParam("account") String account) {
        String response = binanceService.getPaymentHistory(account);
        return ResponseEntity.ok().body(response);
    }
    
    @GetMapping("/pay-transfers")
    public ResponseEntity<List<BuyDollarsDto>> getPayTransfers(@RequestParam("account") String account) {
        String response = binanceService.getPaymentHistory(account);

        try {
            List<Transaction> transactions = parseTransactions(response);
            List<BuyDollarsDto> buyDollarsList = new ArrayList<>();

            for (Transaction transaction : transactions) {
            	double amount = transaction.getAmount(); // Ya es Double

                // Transacciones donde YO soy receptor (compras, entradas)
                if (amount > 0 && transaction.getReceiverInfo().getBinanceId() != null) {
                    
                    String receiverBinanceId = String.valueOf(transaction.getReceiverInfo().getBinanceId());
                    
                    // Verificamos que la cuenta receptor (TÚ) es la misma cuenta que estás consultando
                    AccountBinance accountBinance = accountBinanceRepository.findByReferenceAccount(receiverBinanceId);

                    if (accountBinance != null && accountBinance.getName().equalsIgnoreCase(account)) {
                        BuyDollarsDto buyDollarsDto = new BuyDollarsDto();
                        buyDollarsDto.setDollars(amount);
                        buyDollarsDto.setTasa(0.0); // asignada posteriormente
                        buyDollarsDto.setNameAccount(account);
                        buyDollarsDto.setDate(transaction.getTransactionTime());
                        buyDollarsDto.setIdDeposit(transaction.getTransactionId());
                        buyDollarsDto.setPesos(0.0);

                        buyDollarsList.add(buyDollarsDto);
                    }
                }
            }

            return ResponseEntity.ok(buyDollarsList);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }
    }


    
    
    
    private List<Transaction> parseTransactions(String response) {
        // Asumiendo que la respuesta es un JSON, usa alguna librería como Jackson para convertir el JSON en objetos de tipo Transaction
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode dataNode = rootNode.path("data");

            // Verificamos si la propiedad 'data' está presente y contiene elementos
            if (dataNode.isArray() && dataNode.size() > 0) {
                List<Transaction> transactions = new ArrayList<>();
                for (JsonNode node : dataNode) {
                    // Convertimos cada transacción del JSON a un objeto Transaction
                    Transaction transaction = objectMapper.treeToValue(node, Transaction.class);
                    transactions.add(transaction);
                }
                return transactions;
            } else {
                // Si no hay datos en la propiedad "data", devolvemos una lista vacía
                return new ArrayList<>();
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error parsing the transactions", e);
        }
    }
    
    
    @GetMapping("/sell-transfers")
    public ResponseEntity<List<SellDollarsDto>> getSellTransfers(@RequestParam("account") String account) {
        String response = binanceService.getPaymentHistory(account);

        try {
            List<Transaction> transactions = parseTransactions(response);
            List<SellDollarsDto> sellDollarsList = new ArrayList<>();

            for (Transaction transaction : transactions) {
                double amount = transaction.getAmount();

                // Transacciones donde YO soy pagador (ventas, salidas)
                if (amount < 0 && transaction.getPayerInfo().getBinanceId() != null) {

                    String payerBinanceId = String.valueOf(transaction.getPayerInfo().getBinanceId());

                    // Verificamos que la cuenta pagadora (TÚ) es la misma cuenta que estás consultando
                    AccountBinance accountBinance = accountBinanceRepository.findByReferenceAccount(payerBinanceId);

                    if (accountBinance != null && accountBinance.getName().equalsIgnoreCase(account)) {
                        SellDollarsDto sellDollarsDto = new SellDollarsDto();
                        sellDollarsDto.setDollars(Math.abs(amount)); // Convertir monto a positivo
                        sellDollarsDto.setTasa(0.0); // asignada posteriormente
                        sellDollarsDto.setNameAccount(account);
                        sellDollarsDto.setDate(transaction.getTransactionTime());
                        sellDollarsDto.setIdWithdrawals(transaction.getTransactionId());
                        sellDollarsDto.setPesos(0.0);

                        sellDollarsList.add(sellDollarsDto);
                    }
                }
            }
            return ResponseEntity.ok(sellDollarsList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    
    
  


}
