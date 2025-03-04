package com.binance.web.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class BinanceService {

    private static final String PAYMENTS_API_URL = "https://api.binance.com/sapi/v1/pay/transactions";
    private static final String P2P_ORDERS_API_URL = "https://api.binance.com/sapi/v1/c2c/orderMatch/listUserOrderHistory";

    // Claves API de cada cuenta
    private final String[][] apiKeys = {
            {"MILTON", "EfBN9mFWAxk7CwsZzu37sXIGXyIQnyLVrAs3aqZOLAa3NumayunaGRQIJ6fi4U2r", "NbdiovuQxwgzwANxgZC669Jke5MZJUH3hyLT6BD8iWYz91EVK6e9adOY2Wq4t6nK"},
            {"CESAR", "Ho474mufN8vTwvrZLjj8DdZHxa88JYlCrcPHp1r7UAhwc197So9vmUG9tRhM3XNr", "Ns41sTlvAM3nUzD0qMPE4PW57omuSxOPKdcngudgqVPphExjJC3tWX8kcxwibXDz"},
            {"MARCEL", "vtNXEFCDEYxWpGGipXG210zzq5i2FnJAqmK5LJtRGiq5NRMCJqCQEOcR85SAunUP", "J9eIUXMxwFggHvU2HHp2EiWfNaXGvShSx5UihepHmW1gIjIBe3waZC3JvMUPBfga"}
    };

    public String getPaymentHistory(String account) {
        try {
            String[] credentials = getApiCredentials(account);
            if (credentials == null) return "{\"error\": \"Cuenta no válida.\"}";

            String apiKey = credentials[0];
            String secretKey = credentials[1];

            long timestamp = getServerTime();
            String query = "timestamp=" + timestamp + "&recvWindow=60000";
            String signature = hmacSha256(secretKey, query);
            String url = PAYMENTS_API_URL + "?" + query + "&signature=" + signature;

            HttpResponse<String> response = sendBinanceRequest(url, apiKey);
            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

            if (jsonResponse.has("code") && !jsonResponse.get("code").getAsString().equals("000000")) {
                return "{\"error\": \"" + jsonResponse.get("msg").getAsString() + "\"}";
            }

            return response.body();
        } catch (Exception e) {
            return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
        }
    }

    public String getP2POrders(String account) {
        try {
            String[] credentials = getApiCredentials(account);
            if (credentials == null) return "{\"error\": \"Cuenta no válida.\"}";

            String apiKey = credentials[0];
            String secretKey = credentials[1];

            long timestamp = getServerTime();
            List<JsonObject> allOrders = new ArrayList<>();
            int currentPage = 1;
            int rows = 50; // Binance solo permite 50 registros por solicitud

            while (true) {
                String query = "tradeType=SELL&timestamp=" + timestamp +
                               "&recvWindow=60000&page=" + currentPage + "&rows=" + rows;
                String signature = hmacSha256(secretKey, query);
                String url = P2P_ORDERS_API_URL + "?" + query + "&signature=" + signature;

                HttpResponse<String> response = sendBinanceRequest(url, apiKey);
                String responseBody = response.body();

                // 🔍 DEBUG: Imprimir respuesta de Binance
                System.out.println("🔍 Binance Response (Page " + currentPage + "): " + responseBody);

                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();

                // Si hay error en la respuesta, devolverlo
                if (jsonResponse.has("code") && !jsonResponse.get("code").getAsString().equals("000000")) {
                    return "{\"error\": \"" + jsonResponse.get("msg").getAsString() + "\"}";
                }

                // Obtener las órdenes de la respuesta
                if (!jsonResponse.has("data") || jsonResponse.get("data").isJsonNull()) {
                    break;
                }

                jsonResponse.getAsJsonArray("data").forEach(order -> allOrders.add(order.getAsJsonObject()));

                // Verificar si ya obtuvimos todas las órdenes
                if (jsonResponse.has("total")) {
                    int totalOrders = jsonResponse.get("total").getAsInt();
                    if (allOrders.size() >= totalOrders) {
                        break;
                    }
                }

                currentPage++; // Siguiente página
            }

            JsonObject finalResponse = new JsonObject();
            finalResponse.add("data", JsonParser.parseString(allOrders.toString()));
            return finalResponse.toString();

        } catch (Exception e) {
            return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
        }
    }







    private long getServerTime() throws Exception {
        String url = "https://api.binance.com/api/v3/time";
        HttpResponse<String> response = sendBinanceRequest(url, null);
        JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

        // 🔥 Verificar si "serverTime" existe antes de usar getAsLong()
        if (!jsonResponse.has("serverTime") || jsonResponse.get("serverTime").isJsonNull()) {
            throw new RuntimeException("No se pudo obtener el timestamp del servidor Binance");
        }

        return jsonResponse.get("serverTime").getAsLong();
    }

    private HttpResponse<String> sendBinanceRequest(String url, String apiKey) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();
        if (apiKey != null) {
            requestBuilder.header("X-MBX-APIKEY", apiKey);
        }

        return HttpClient.newHttpClient().send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String[] getApiCredentials(String account) {
        for (String[] key : apiKeys) {
            if (key[0].equals(account)) {
                return new String[]{key[1], key[2]};
            }
        }
        return null;
    }

    private String hmacSha256(String secretKey, String data) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secretKeySpec);
            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar la firma HMAC SHA256", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
