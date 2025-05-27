package com.binance.web.BinanceAPI;
import com.binance.web.BuyDollars.BuyDollarsDto;

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
}

