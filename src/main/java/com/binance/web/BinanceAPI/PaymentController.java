package com.binance.web.BinanceAPI;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.BuyDollars.BuyDollarsService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.SellDollars.SellDollarsDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.binance.web.model.Transaction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;


@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PaymentController {

    private final BinanceService binanceService;
    private final BuyDollarsService buyDollarsService;
    private final AccountBinanceRepository accountBinanceRepository; 
    @Autowired
    private BuyDollarsRepository buyDollarsRepository;
    @Autowired
    private SellDollarsRepository sellDollarsRepository;

    public PaymentController(BinanceService binanceService, BuyDollarsService buyDollarsService, 
    		AccountBinanceRepository accountBinanceRepository) {
    	
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

    
    
  
    
    
    
    
    
    
    
    
    @GetMapping("/transactions")
    public ResponseEntity<List<BuyDollarsDto>> getBuyTransactions(@RequestParam("account") String account) {
        List<BuyDollarsDto> buyDollarsList = new ArrayList<>();

        try {
            // Obtener el historial de pagos y procesar las entradas (compras)
            String response = binanceService.getPaymentHistory(account);
            List<Transaction> transactions = parseTransactions(response);

            for (Transaction transaction : transactions) {
                double amount = transaction.getAmount();
                
                // Transacciones donde YO soy receptor (compras, entradas)
                if (amount > 0 && transaction.getReceiverInfo().getBinanceId() != null) {
                    String receiverBinanceId = String.valueOf(transaction.getReceiverInfo().getBinanceId());

                    // Verificamos que la cuenta receptor (TÚ) es la misma cuenta que estás consultando
                    AccountBinance accountBinance = accountBinanceRepository.findByReferenceAccount(receiverBinanceId);

                    if (accountBinance != null && accountBinance.getName().equalsIgnoreCase(account)) {
                        BuyDollarsDto buyDollarsDto = new BuyDollarsDto();
                        buyDollarsDto.setDollars(amount);
                        buyDollarsDto.setTasa(0.0); // Tasa asignada posteriormente
                        buyDollarsDto.setNameAccount(account);
                        buyDollarsDto.setDate(transaction.getTransactionTime());
                        buyDollarsDto.setIdDeposit(transaction.getTransactionId());
                        buyDollarsDto.setPesos(0.0);
                        
                        buyDollarsList.add(buyDollarsDto);
                    }
                }
            }

            // Obtener depósitos (compra en Spot)
            String depositResponse = binanceService.getSpotDeposits(account, 1000);
            // Procesamos los depósitos de Spot de forma similar a las compras
            buyDollarsList.addAll(processDeposits(depositResponse, account));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }

        return ResponseEntity.ok(buyDollarsList);
    }

    private List<BuyDollarsDto> processDeposits(String response, String account) {
        List<BuyDollarsDto> buyDollarsList = new ArrayList<>();
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response);

            JsonNode dataNode;
            if (rootNode.isArray()) {
                // El JSON ya es directamente un array (caso spotDeposits)
                dataNode = rootNode;
            } else {
                // Normalmente viene como objeto con campo "data"
                dataNode = rootNode.path("data");
            }

            if (!dataNode.isArray()) {
                return buyDollarsList;
            }

            // Filtrar depósitos ya registrados
            Set<String> assignedIds = buyDollarsRepository.findAll().stream()
                .map(buy -> buy.getIdDeposit())
                .collect(Collectors.toSet());

            for (JsonNode deposit : dataNode) {
                String id = deposit.path("id").asText();
                double amount = deposit.path("amount").asDouble();

                if (!assignedIds.contains(id)) {
                    BuyDollarsDto dto = new BuyDollarsDto();
                    dto.setDollars(amount);
                    dto.setTasa(0.0);
                    dto.setNameAccount(account);
                    dto.setIdDeposit(id);
                    dto.setPesos(0.0);

                    // Puedes usar insertTime o completeTime como fecha
                    long timestamp = deposit.path("insertTime").asLong(0);
                    if (timestamp > 0) {
                        dto.setDate(new Date(timestamp));
                    }

                    buyDollarsList.add(dto);
                }
            }

        } catch (Exception e) {
            e.printStackTrace(); // puedes loggear más claro si lo necesitas
        }

        return buyDollarsList;
    }


    
    @GetMapping("/sales")
    public ResponseEntity<List<SellDollarsDto>> getSellTransactions(@RequestParam("account") String account) {
        List<SellDollarsDto> sellDollarsList = new ArrayList<>();

        try {
            // Obtener el historial de pagos y procesar las salidas (ventas)
            String response = binanceService.getPaymentHistory(account);
            List<Transaction> transactions = parseTransactions(response);

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
                        sellDollarsDto.setTasa(0.0); // Tasa asignada posteriormente
                        sellDollarsDto.setNameAccount(account);
                        sellDollarsDto.setDate(transaction.getTransactionTime());
                        sellDollarsDto.setIdWithdrawals(transaction.getTransactionId());
                        sellDollarsDto.setPesos(0.0);

                        sellDollarsList.add(sellDollarsDto);
                    }
                }
            }

            // Obtener retiros (venta en Spot)
            String withdrawResponse = binanceService.getSpotWithdrawals(account, 100);
            // Procesamos los retiros de Spot de forma similar a las ventas
            sellDollarsList.addAll(processWithdrawals(withdrawResponse, account));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok(new ArrayList<>());
        }

        return ResponseEntity.ok(sellDollarsList);
    }

    private List<SellDollarsDto> processWithdrawals(String response, String account) {
        List<SellDollarsDto> sellDollarsList = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response);

            JsonNode dataNode;
            if (rootNode.isArray()) {
                dataNode = rootNode;
            } else {
                dataNode = rootNode.path("data");
            }

            if (!dataNode.isArray()) {
                return sellDollarsList;  // No es un array, devolver vacío
            }

            // IDs ya guardados
            Set<String> assignedIds = sellDollarsRepository.findAll().stream()
                .map(sell -> sell.getIdWithdrawals())
                .collect(Collectors.toSet());

            for (JsonNode withdrawal : dataNode) {
                String id = withdrawal.path("id").asText();
                double amount = withdrawal.path("amount").asDouble();

                if (!assignedIds.contains(id)) {
                    SellDollarsDto dto = new SellDollarsDto();
                    dto.setDollars(Math.abs(amount));
                    dto.setTasa(0.0);
                    dto.setNameAccount(account);
                    dto.setIdWithdrawals(id);
                    dto.setPesos(0.0);

                    // ✅ Convertir applyTime (formato string) a Date
                    String applyTimeStr = withdrawal.path("applyTime").asText(null);
                    if (applyTimeStr != null && !applyTimeStr.isEmpty()) {
                        try {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                            LocalDateTime dateTime = LocalDateTime.parse(applyTimeStr, formatter);
                            ZonedDateTime zonedDateTime = dateTime.atZone(ZoneId.of("America/Bogota")); // ajusta la zona si es necesario
                            dto.setDate(Date.from(zonedDateTime.toInstant()));
                        } catch (DateTimeParseException e) {
                            e.printStackTrace(); // log si hay formato inesperado
                        }
                    }

                    sellDollarsList.add(dto);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sellDollarsList;
    }


    
    
    @GetMapping("/transactions/today")
    public ResponseEntity<List<BuyDollarsDto>> getTodayBuyTransactions(@RequestParam("account") String account) {
        List<BuyDollarsDto> all = getBuyTransactions(account).getBody();
        
        if (all == null) return ResponseEntity.ok(List.of());

        LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));

        List<BuyDollarsDto> filtered = all.stream()
            .filter(b -> b.getDate() != null &&
                b.getDate().toInstant().atZone(ZoneId.of("America/Bogota")).toLocalDate().isEqual(today))
            .collect(Collectors.toList());

        return ResponseEntity.ok(filtered);
    }

    
    
    @GetMapping("/sales/today")
    public ResponseEntity<List<SellDollarsDto>> getTodaySellTransactions(@RequestParam("account") String account) {
        List<SellDollarsDto> all = getSellTransactions(account).getBody();

        if (all == null) return ResponseEntity.ok(List.of());

        LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));

        List<SellDollarsDto> filtered = all.stream()
            .filter(s -> s.getDate() != null &&
                s.getDate().toInstant().atZone(ZoneId.of("America/Bogota")).toLocalDate().isEqual(today))
            .collect(Collectors.toList());

        return ResponseEntity.ok(filtered);
    }



}
