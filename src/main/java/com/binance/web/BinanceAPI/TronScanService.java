package com.binance.web.BinanceAPI;
import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.SellDollars.SellDollarsDto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;



@Service
public class TronScanService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TronScanService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public String getTransactions(String address) {
        String url = "https://apilist.tronscanapi.com/api/transaction?sort=-timestamp&count=true&limit=20&start=0&address=" + address;
        return restTemplate.getForObject(url, String.class);
    }
    
    public String getTRC20TransfersUsingTronGrid(String address) {
        String url = "https://api.trongrid.io/v1/accounts/" + address + "/transactions/trc20?limit=50";
        HttpHeaders headers = new HttpHeaders();
        headers.set("TRON-PRO-API-KEY", "a2932898-5552-453f-88f7-7f4615aa1c08");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return response.getBody();
    }


    public List<BuyDollarsDto> parseIncomingTransactions(String jsonResponse, String walletAddress, Set<String> assignedIds) {
        List<BuyDollarsDto> result = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.path("data");

            if (data.isArray()) {
                for (JsonNode tx : data) {
                    String toAddress = tx.path("toAddress").asText();
                    String fromAddress = tx.path("ownerAddress").asText();
                    String txId = tx.path("hash").asText();
                    long timestamp = tx.path("timestamp").asLong();
                    String amountStr = tx.path("amount").asText("0");

                    if (toAddress.equalsIgnoreCase(walletAddress) && !assignedIds.contains(txId)) {
                        double amount = Double.parseDouble(amountStr) / 1_000_000.0;

                        BuyDollarsDto dto = new BuyDollarsDto();
                        dto.setDollars(amount);
                        dto.setTasa(0.0);
                        dto.setNameAccount("TRUST");
                        dto.setIdDeposit(txId);
                        dto.setPesos(0.0);
                        dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota")));

                        result.add(dto);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
    
    public List<BuyDollarsDto> parseOutgoingTransactions(String jsonResponse, String walletAddress, Set<String> assignedIds) {
        List<BuyDollarsDto> result = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.path("data");

            if (data.isArray()) {
                for (JsonNode tx : data) {
                    String fromAddress = tx.path("ownerAddress").asText();
                    String toAddress = tx.path("toAddress").asText();
                    String txId = tx.path("hash").asText();
                    long timestamp = tx.path("timestamp").asLong();
                    String amountStr = tx.path("amount").asText("0");

                    if (fromAddress.equalsIgnoreCase(walletAddress) && !assignedIds.contains(txId)) {
                        double amount = Double.parseDouble(amountStr) / 1_000_000.0;

                        BuyDollarsDto dto = new BuyDollarsDto();
                        dto.setDollars(amount);
                        dto.setTasa(0.0);
                        dto.setNameAccount("TRUST_OUT");  // Para distinguir salida
                        dto.setIdDeposit(txId);
                        dto.setPesos(0.0);
                        dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota")));

                        result.add(dto);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
    
    
    
    
    
    


    
    
    
    
    
    public List<BuyDollarsDto> parseTRC20IncomingUSDTTransfers(String jsonResponse, String walletAddress, Set<String> assignedIds) {
        List<BuyDollarsDto> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode tx : data) {
                    String toAddress = tx.path("to").asText();
                    String txId = tx.path("transaction_id").asText();
                    JsonNode tokenInfo = tx.path("token_info");
                    String symbol = tokenInfo.path("symbol").asText();
                    if (toAddress.equalsIgnoreCase(walletAddress) && symbol.equalsIgnoreCase("USDT") && !assignedIds.contains(txId)) {
                        double amount = Double.parseDouble(tx.path("value").asText("0")) / 1_000_000.0;
                        long timestamp = tx.path("block_timestamp").asLong();

                        BuyDollarsDto dto = new BuyDollarsDto();
                        dto.setDollars(amount);
                        dto.setTasa(0.0);
                        dto.setNameAccount("TRUST");
                        dto.setIdDeposit(txId);
                        dto.setPesos(0.0);
                        dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota")));
                        result.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<SellDollarsDto> parseTRC20OutgoingUSDTTransfers(String jsonResponse, String walletAddress, Set<String> assignedIds) {
        List<SellDollarsDto> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode tx : data) {
                    String fromAddress = tx.path("from").asText();
                    String txId = tx.path("transaction_id").asText();
                    JsonNode tokenInfo = tx.path("token_info");
                    String symbol = tokenInfo.path("symbol").asText();
                    if (fromAddress.equalsIgnoreCase(walletAddress) && symbol.equalsIgnoreCase("USDT") && !assignedIds.contains(txId)) {
                        double amount = Double.parseDouble(tx.path("value").asText("0")) / 1_000_000.0;
                        long timestamp = tx.path("block_timestamp").asLong();

                        SellDollarsDto dto = new SellDollarsDto();
                        dto.setDollars(amount);
                        dto.setTasa(0.0);
                        dto.setNameAccount("TRUST");
                        dto.setIdWithdrawals(txId); // Ajusta según el nombre del campo en SellDollarsDto
                        dto.setPesos(0.0);
                        dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota")));
                        result.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }



}

