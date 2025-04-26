package com.binance.web.BinanceAPI;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
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
            {"MARCEL", "vtNXEFCDEYxWpGGipXG210zzq5i2FnJAqmK5LJtRGiq5NRMCJqCQEOcR85SAunUP", "J9eIUXMxwFggHvU2HHp2EiWfNaXGvShSx5UihepHmW1gIjIBe3waZC3JvMUPBfga"},
            {"SONIA", "N0lUyNy3rlgNxq6XKlKdjxVLppvBwPl1Bxi7FeDZ82G7X47oL2tor20vprJaLZLk", "Nqhxi7XMzNmQMk4phC442bkA368L8Toi0EAidGOJhal2f72olp5FMhOY7OoaehUg"}
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

            return sendBinanceRequestWithProxy(url, apiKey);

        } catch (Exception e) {
            return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
        }
    }

    public String getP2POrdersInRange(String account, long startTime, long endTime) {
        try {
            String[] credentials = getApiCredentials(account);
            if (credentials == null) return "{\"error\": \"Cuenta no válida.\"}";

            String apiKey = credentials[0];
            String secretKey = credentials[1];

            List<JsonObject> allOrders = new ArrayList<>();
            int currentPage = 1;
            int rows = 50;

            while (true) {
                long timestamp = getServerTime();
                String query = "tradeType=SELL" +
                               "&startTimestamp=" + startTime +
                               "&endTimestamp=" + endTime +
                               "&page=" + currentPage +
                               "&rows=" + rows +
                               "&recvWindow=60000" +
                               "&timestamp=" + timestamp;

                String signature = hmacSha256(secretKey, query);
                String url = P2P_ORDERS_API_URL + "?" + query + "&signature=" + signature;

                String response = sendBinanceRequestWithProxy(url, apiKey);
                JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

                if (jsonResponse.has("code") && !jsonResponse.get("code").getAsString().equals("000000")) {
                    return "{\"error\": \"" + jsonResponse.get("msg").getAsString() + "\"}";
                }

                JsonArray dataArray = jsonResponse.getAsJsonArray("data");
                if (dataArray == null || dataArray.size() == 0) break;

                dataArray.forEach(order -> allOrders.add(order.getAsJsonObject()));

                int totalOrders = jsonResponse.has("total") ? jsonResponse.get("total").getAsInt() : 0;
                if (allOrders.size() >= totalOrders) break;

                currentPage++;
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
        String response = sendBinanceRequestWithProxy(url, null);

        // Convertir la respuesta en JSON
        JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

        if (!jsonResponse.has("serverTime") || jsonResponse.get("serverTime").isJsonNull()) {
            throw new RuntimeException("No se pudo obtener el timestamp del servidor Binance");
        }

        return jsonResponse.get("serverTime").getAsLong();
    }

    private String sendBinanceRequestWithProxy(String url, String apiKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");

        if (apiKey != null) {
            connection.setRequestProperty("X-MBX-APIKEY", apiKey);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("Error HTTP: " + responseCode);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
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

    public String getP2POrderLatest(String account) {
        try {
            String[] credentials = getApiCredentials(account);
            if (credentials == null) return "{\"error\": \"Cuenta no válida.\"}";

            String apiKey = credentials[0];
            String secretKey = credentials[1];

            long timestamp = getServerTime();
            List<JsonObject> allOrders = new ArrayList<>();
            int currentPage = 1;
            int rows = 50;

            while (true) {
                String query = "tradeType=SELL&timestamp=" + timestamp +
                               "&recvWindow=60000&page=" + currentPage + "&rows=" + rows;
                String signature = hmacSha256(secretKey, query);
                String url = P2P_ORDERS_API_URL + "?" + query + "&signature=" + signature;

                String response = sendBinanceRequestWithProxy(url, apiKey);
                JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

                if (jsonResponse.has("code") && !jsonResponse.get("code").getAsString().equals("000000")) {
                    return "{\"error\": \"" + jsonResponse.get("msg").getAsString() + "\"}";
                }

                if (!jsonResponse.has("data") || jsonResponse.get("data").isJsonNull()) {
                    break;
                }

                jsonResponse.getAsJsonArray("data").forEach(order -> allOrders.add(order.getAsJsonObject()));

                if (allOrders.size() >= 100) {
                    break;
                }

                currentPage++;
            }

            int startIndex = Math.max(0, allOrders.size() - 100);
            List<JsonObject> last100Orders = allOrders.subList(startIndex, allOrders.size());

            JsonObject finalResponse = new JsonObject();
            finalResponse.add("data", JsonParser.parseString(last100Orders.toString()));
            return finalResponse.toString();

        } catch (Exception e) {
            return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
        }
    }

    public String getSpotOrders(String account, String symbol, int limit) {
        try {
            String[] credentials = getApiCredentials(account);
            if (credentials == null) return "{\"error\": \"Cuenta no válida.\"}";

            String apiKey = credentials[0];
            String secretKey = credentials[1];

            long timestamp = getServerTime();
            String query = "symbol=" + symbol +
                           "&limit=" + limit +
                           "&timestamp=" + timestamp +
                           "&recvWindow=60000";

            String signature = hmacSha256(secretKey, query);
            String url = "https://api.binance.com/api/v3/allOrders?" + query + "&signature=" + signature;

            return sendBinanceRequestWithProxy(url, apiKey);

        } catch (Exception e) {
            return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
        }
    }

    public String getFuturesOrders(String account, String symbol, int limit) {
        try {
            String[] credentials = getApiCredentials(account);
            if (credentials == null) return "{\"error\": \"Cuenta no válida.\"}";

            String apiKey = credentials[0];
            String secretKey = credentials[1];

            long timestamp = getServerTime();
            String query = "symbol=" + symbol +
                           "&limit=" + limit +
                           "&timestamp=" + timestamp +
                           "&recvWindow=60000";

            String signature = hmacSha256(secretKey, query);
            String url = "https://fapi.binance.com/fapi/v1/allOrders?" + query + "&signature=" + signature;

            return sendBinanceRequestWithProxy(url, apiKey);

        } catch (Exception e) {
            return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
        }
    }

    public String getFuturesPositions(String account) {
        try {
            String[] credentials = getApiCredentials(account);
            if (credentials == null) return "{\"error\": \"Cuenta no válida.\"}";

            String apiKey = credentials[0];
            String secretKey = credentials[1];

            long timestamp = getServerTime();
            String query = "timestamp=" + timestamp + "&recvWindow=60000";
            String signature = hmacSha256(secretKey, query);
            String url = "https://fapi.binance.com/fapi/v2/positionRisk?" + query + "&signature=" + signature;

            return sendBinanceRequestWithProxy(url, apiKey);

        } catch (Exception e) {
            return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
        }
    }

    // 🔸 Esto trae los depósitos/compras que aparecen en billetera spot
    public String getSpotDeposits(String account, int limit) {
        try {
            String[] credentials = getApiCredentials(account);
            if (credentials == null) return "{\"error\": \"Cuenta no válida.\"}";

            String apiKey = credentials[0];
            String secretKey = credentials[1];

            long timestamp = getServerTime();
            String query = "limit=" + limit +
                           "&timestamp=" + timestamp +
                           "&recvWindow=60000";

            String signature = hmacSha256(secretKey, query);
            String url = "https://api.binance.com/sapi/v1/capital/deposit/hisrec?" + query + "&signature=" + signature;

            return sendBinanceRequestWithProxy(url, apiKey);

        } catch (Exception e) {
            return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
        }
    }

    // 🔸 Esto trae los retiros/ventas que aparecen en billetera spot
    public String getSpotWithdrawals(String account, int limit) {
        try {
            String[] credentials = getApiCredentials(account);
            if (credentials == null) return "{\"error\": \"Cuenta no válida.\"}";

            String apiKey = credentials[0];
            String secretKey = credentials[1];

            long timestamp = getServerTime();
            String query = "limit=" + limit +
                           "&timestamp=" + timestamp +
                           "&recvWindow=60000";

            String signature = hmacSha256(secretKey, query);
            String url = "https://api.binance.com/sapi/v1/capital/withdraw/history?" + query + "&signature=" + signature;

            return sendBinanceRequestWithProxy(url, apiKey);

        } catch (Exception e) {
            return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
        }
    }

	public String getSpotBalances(String upperCase) {
		// TODO Auto-generated method stub
		return null;
	}

	public String getFuturesBalances(String upperCase) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public String getSpotBalanceByAsset(String account, String asset) {
	    try {
	        String[] credentials = getApiCredentials(account);
	        if (credentials == null) return "{\"error\": \"Cuenta no válida.\"}";

	        String apiKey = credentials[0];
	        String secretKey = credentials[1];

	        long timestamp = getServerTime();
	        String query = "timestamp=" + timestamp + "&recvWindow=60000";
	        String signature = hmacSha256(secretKey, query);
	        String url = "https://api.binance.com/api/v3/account?" + query + "&signature=" + signature;

	        String response = sendBinanceRequestWithProxy(url, apiKey);
	        JsonObject accountData = JsonParser.parseString(response).getAsJsonObject();
	        JsonArray balances = accountData.getAsJsonArray("balances");

	        for (int i = 0; i < balances.size(); i++) {
	            JsonObject balance = balances.get(i).getAsJsonObject();
	            if (balance.get("asset").getAsString().equalsIgnoreCase(asset)) {
	                JsonObject result = new JsonObject();
	                result.add("balance", balance);
	                return result.toString();
	            }
	        }

	        return "{\"error\": \"Activo no encontrado.\"}";

	    } catch (Exception e) {
	        return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
	    }
	}

}
