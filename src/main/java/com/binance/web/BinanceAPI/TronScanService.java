package com.binance.web.BinanceAPI;
import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.SellDollars.SellDollarsDto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
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
    private static final String TRONGRID_API_KEY = "a2932898-5552-453f-88f7-7f4615aa1c08";

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
        headers.set("TRON-PRO-API-KEY", TRONGRID_API_KEY);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        return response.getBody();
    }
    
    public JsonNode getTransactionDetailsFromTronScan(String txId) {
        String url = "https://apilist.tronscanapi.com/api/transaction-info?hash=" + txId;
        try {
            System.out.println("DEBUG: Llamando a TronScan para obtener detalles de la transacción: " + txId);
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            System.out.println("DEBUG: Respuesta de TronScan: " + root.toPrettyString());
            return root;
        } catch (Exception e) {
            System.err.println("Error al obtener detalles de la transacción de TronScan: " + txId);
            e.printStackTrace();
            return objectMapper.createObjectNode();
        }
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

    //ventas de tron
    public List<SellDollarsDto> parseTRC20OutgoingUSDTTransfers(
            String jsonResponse,
            String walletAddress,
            String accountName,
            Set<String> assignedIds) {

        List<SellDollarsDto> result = new ArrayList<>();
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode tx : data) {
                    String fromAddress = tx.path("from").asText();
                    String txId = tx.path("transaction_id").asText();
                    JsonNode tokenInfo = tx.path("token_info");
                    String symbol = tokenInfo.path("symbol").asText();

                    if (fromAddress.equalsIgnoreCase(walletAddress) &&
                        symbol.equalsIgnoreCase("USDT") &&
                        !assignedIds.contains(txId)) {

                        double amount = Double.parseDouble(tx.path("value").asText("0")) / 1_000_000.0;
                        long timestamp = tx.path("block_timestamp").asLong();
                        LocalDate fechaTx = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(timestamp),
                                ZoneId.of("America/Bogota")
                        ).toLocalDate();
                        
                        // NOTA: La transacción del ejemplo (3b61add9...) es de 2025-08-12.
                        // Si estás probando hoy (12 de agosto de 2025), la condición `fechaTx.isEqual(hoy)`
                        // se cumplirá. En otro día, tendrás que ajustar esto para probar.
                        if (fechaTx.isEqual(hoy)) { 
                            SellDollarsDto dto = new SellDollarsDto();
                            dto.setDollars(amount);
                            dto.setTasa(0.0);
                            dto.setNameAccount(accountName);
                            dto.setIdWithdrawals(txId);
                            dto.setPesos(0.0);
                            dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp),
                                    ZoneId.of("America/Bogota")));

                            // --- Calcular comisión usando la llamada a TronScan ---
                            JsonNode txDetails = getTransactionDetailsFromTronScan(txId);
                            double feeInSun = txDetails.path("cost").path("energy_fee").asDouble(0.0);
                            System.out.println("DEBUG: Fee en Sun para " + txId + ": " + feeInSun);

                            double feeTRX = feeInSun / 1_000_000.0;
                            System.out.println("DEBUG: Fee en TRX: " + feeTRX);
                            
                            if (feeTRX > 0) {
                                double trxPriceAtTx = getTRXPriceAt(timestamp);
                                double feeInUSDT = feeTRX * trxPriceAtTx;
                                System.out.println("DEBUG: Precio de TRX en el momento: " + trxPriceAtTx);
                                
                                dto.setComision(feeInUSDT);
                                dto.setEquivalenteciaTRX(feeTRX); 
                            } else {
                                dto.setComision(0.0);
                                dto.setEquivalenteciaTRX(0.0);
                            }

                            result.add(dto);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    /**
     * Consulta el precio histórico de TRX/USDT en el momento exacto.
     * Puedes implementarlo usando la API de Binance:
     * https://api.binance.com/api/v3/klines?symbol=TRXUSDT&interval=1m&startTime=...&endTime=...
     */
    private double getTRXPriceAt(long timestamp) {
        try {
            // La API de Binance requiere timestamps en milisegundos
            // Se busca la vela de 1 minuto que contenga el timestamp de la transacción.
            long oneMinuteInMillis = 60 * 1000;
            long startTime = (timestamp / oneMinuteInMillis) * oneMinuteInMillis;
            long endTime = startTime + oneMinuteInMillis;

            String url = String.format(
                "https://api.binance.com/api/v3/klines?symbol=TRXUSDT&interval=1m&startTime=%d&endTime=%d",
                startTime,
                endTime
            );

            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(url, String.class);
            JsonNode array = objectMapper.readTree(response);

            if (array.isArray() && array.size() > 0) {
                // El precio de cierre (close price) está en el índice 4
                // El valor viene como String, lo convertimos a double
                JsonNode candle = array.get(0);
                return candle.get(4).asDouble();
            } else {
                // Manejar el caso de que no se encuentre el precio.
                // Puedes loguear un error o devolver un valor predeterminado.
                System.err.println("No se encontró el precio de TRX para el timestamp: " + timestamp);
                return 0.0;
            }
        } catch (Exception e) {
            System.err.println("Error al obtener el precio de TRX: " + e.getMessage());
            e.printStackTrace();
            return 0.0;
        }
    }

    public List<BuyDollarsDto> parseTRC20IncomingUSDTTransfers(String jsonResponse, String walletAddress, String accountName, Set<String> assignedIds) {
        List<BuyDollarsDto> result = new ArrayList<>();
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

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
                        LocalDate fechaTx = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota")).toLocalDate();

                        if (fechaTx.isEqual(hoy)) {
                            BuyDollarsDto dto = new BuyDollarsDto();
                            dto.setDollars(amount);
                            dto.setTasa(0.0);
                            dto.setNameAccount(accountName);
                            dto.setIdDeposit(txId);
                            dto.setPesos(0.0);
                            dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota")));
                            result.add(dto);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


	/*
	 * public List<SellDollarsDto> parseTRC20OutgoingUSDTTransfers(String
	 * jsonResponse, String walletAddress, Set<String> assignedIds) {
	 * List<SellDollarsDto> result = new ArrayList<>(); try { JsonNode root =
	 * objectMapper.readTree(jsonResponse); JsonNode data = root.path("data"); if
	 * (data.isArray()) { for (JsonNode tx : data) { String fromAddress =
	 * tx.path("from").asText(); String txId = tx.path("transaction_id").asText();
	 * JsonNode tokenInfo = tx.path("token_info"); String symbol =
	 * tokenInfo.path("symbol").asText(); if
	 * (fromAddress.equalsIgnoreCase(walletAddress) &&
	 * symbol.equalsIgnoreCase("USDT") && !assignedIds.contains(txId)) { double
	 * amount = Double.parseDouble(tx.path("value").asText("0")) / 1_000_000.0; long
	 * timestamp = tx.path("block_timestamp").asLong();
	 * 
	 * SellDollarsDto dto = new SellDollarsDto(); dto.setDollars(amount);
	 * dto.setTasa(0.0); dto.setNameAccount("TRUST"); dto.setIdWithdrawals(txId); //
	 * Ajusta según el nombre del campo en SellDollarsDto dto.setPesos(0.0);
	 * dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp),
	 * ZoneId.of("America/Bogota"))); result.add(dto); } } } } catch (Exception e) {
	 * e.printStackTrace(); } return result; }
	 */
    
    
    
    
    //OBTIENE EL SALDO DE LA CUENTA EN USDT
    public double getTotalAssetTokenOverview(String walletAddress) {
        try {
            String url = "https://apilist.tronscanapi.com/api/account/token_asset_overview?address=" + walletAddress;
            HttpHeaders headers = new HttpHeaders();
            // Si tienes API key de Tronscan, añádela aquí:
            // headers.set("TRON-PRO-API-KEY", "TU_API_KEY");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> resp = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(resp.getBody());
            return root.path("totalAssetInUsd").asDouble(0.0);
        } catch (Exception e) {
            e.printStackTrace();
            return 0.0;
        }
    }

}

