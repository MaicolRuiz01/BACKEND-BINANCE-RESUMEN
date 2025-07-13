package com.binance.web.BinanceAPI;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class BinanceService {

    private static final String PAYMENTS_API_URL = "https://api.binance.com/sapi/v1/pay/transactions";
    private static final String P2P_ORDERS_API_URL = "https://api.binance.com/sapi/v1/c2c/orderMatch/listUserOrderHistory";

    // Claves API de cada cuenta
    private final String[][] apiKeys = {
            {"MILTON", "EfBN9mFWAxk7CwsZzu37sXIGXyIQnyLVrAs3aqZOLAa3NumayunaGRQIJ6fi4U2r", "NbdiovuQxwgzwANxgZC669Jke5MZJUH3hyLT6BD8iWYz91EVK6e9adOY2Wq4t6nK"},
            {"CESAR", "Ho474mufN8vTwvrZLjj8DdZHxa88JYlCrcPHp1r7UAhwc197So9vmUG9tRhM3XNr", "Ns41sTlvAM3nUzD0qMPE4PW57omuSxOPKdcngudgqVPphExjJC3tWX8kcxwibXDz"},
            {"MARCEL", "vtNXEFCDEYxWpGGipXG210zzq5i2FnJAqmK5LJtRGiq5NRMCJqCQEOcR85SAunUP", "J9eIUXMxwFggHvU2HHp2EiWfNaXGvShSx5UihepHmW1gIjIBe3waZC3JvMUPBfga"},
            {"SONIA", "N0lUyNy3rlgNxq6XKlKdjxVLppvBwPl1Bxi7FeDZ82G7X47oL2tor20vprJaLZLk", "Nqhxi7XMzNmQMk4phC442bkA368L8Toi0EAidGOJhal2f72olp5FMhOY7OoaehUg"},
            {"BERNANDA","UxPVjnvpZBgKgxHV6Qbds15TlTtFrBgyOycsw1Enj2ybiZFDc6ewk51ys3Sxvgxm","GCliNB78z1FJkx5542QeY3PXsUBqJJMPQNZ6MmKAeEUjhItMLIhwKNhUw6pSCH8E"},
            {"RIK", "vMSWVD5tca6o73oCOLcxefK0W4FA30nwUvAFhrRlpEfmOyX1Jv6Y94llAYJJT9HU", "nknvsAdsO1BWApJ7119jJKWTHWbT2HCGP0PJNrXsFiRR8GlWxHqmt25DyhNxu7Gw"},
            {"JULIO","M2GxXbjasdJPx6oWXhr3aKoeUB6WnfhFDZ02RmdTEhJlFkWMZkWqUCgfynM5iakd", "CDOTuuPusPaZSZakDmISILjjmSNfW6v9A3QGqdh7F43OwD7MT5LQgJowto2VFBlt"}
    };
    
    public List<String> getAllAccountNames() {
        return Arrays.stream(apiKeys)
                     .map(keys -> keys[0])
                     .collect(Collectors.toList());
    }

    
    
    
    //metodo para obtener los movimientos de binancepay las que se hacen por correo

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
    
 // En BinanceService.java
    public String getAllSpotTradeOrdersTRXUSDT() {
        try {
            List<JsonObject> allTrades = new ArrayList<>();

            for (String[] accountEntry : apiKeys) {
                String account = accountEntry[0];
                String apiKey = accountEntry[1];
                String secretKey = accountEntry[2];

                // Consultar solo TRXUSDT con límite 100
                String response = getSpotOrders(account, "TRXUSDT", 20);
                JsonArray tradesArray = JsonParser.parseString(response).getAsJsonArray();

                for (JsonElement el : tradesArray) {
                    JsonObject trade = el.getAsJsonObject();
                    trade.addProperty("account", account);
                    allTrades.add(trade);
                }
            }

            return new Gson().toJson(allTrades);

        } catch (Exception e) {
            return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
        }
    }

    //
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
  //estp es para futures
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
    //estp es para futures
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
	
	
	//este metodo busca el valor de los trx en una fecha que le mandamos, esto lo uso para pasar los trx a usdt 
	//en traspasos y ventas, podemos ver en el spotcontroller que se usa este metodo
	
	public Double getHistoricalPriceTRXUSDT(LocalDateTime fecha) {
	    try {
	        long startTime = fecha.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();
	        long endTime = startTime + 60000; // +1 minuto

	        String url = "https://api.binance.com/api/v3/klines?symbol=TRXUSDT&interval=1m&startTime=" + startTime + "&endTime=" + endTime;

	        String response = sendBinanceRequestWithProxy(url, null);

	        JsonArray klines = JsonParser.parseString(response).getAsJsonArray();
	        if (klines.size() > 0) {
	            JsonArray firstKline = klines.get(0).getAsJsonArray();
	            String closePriceStr = firstKline.get(4).getAsString(); // índice 4 = precio de cierre
	            return Double.parseDouble(closePriceStr);
	        }

	    } catch (Exception e) {
	        // Log error si quieres
	    }
	    return null; // si falla o no encuentra precio
	}


	public String getGeneralBalance(String account) {
	    try {
	        // 1) Credenciales
	        String[] creds = getApiCredentials(account);
	        if (creds == null) {
	            return "{\"error\": \"Cuenta no válida.\"}";
	        }

	        // 2) Spot
	        String spotResp = getSpotBalance(account);
	        JsonObject spotJson = JsonParser.parseString(spotResp).getAsJsonObject();
	        double spotBalance = getJsonDouble(spotJson, "totalBalance");

	        // 3) Futures
	        String futResp = getFuturesBalance(account);
	        JsonObject futJson = JsonParser.parseString(futResp).getAsJsonObject();
	        double futuresBalance = getJsonDouble(futJson, "totalBalance");

	        // 4) Funding (free + freeze)
	        double fundingBalance = getFundingAssetBalance(account, "USDT");

	        // 5) Sumar todo
	        double totalBalance = spotBalance + futuresBalance + fundingBalance;

	        // 6) Devolver JSON
	        return String.valueOf(totalBalance);


	    } catch (Exception e) {
	        return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
	    }
	}


	// Método auxiliar para manejar conversiones de valores de JSON a Double con validación
	private double getJsonDouble(JsonObject object, String key) {
	    if (object.has(key) && !object.get(key).isJsonNull()) {
	        return object.get(key).getAsDouble();
	    } else {
	        // Si el valor no existe o es null, devuelve 0.0
	        System.out.println("Clave '" + key + "' no existe o es null.");
	        return 0.0;
	    }
	}

	


	private String getSpotBalance(String account) {
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

	        // Verificar si la respuesta es válida y no nula
	        if (response == null || response.isEmpty()) {
	            return "{\"error\": \"La respuesta de Spot Wallet está vacía o nula.\"}";
	        }

	        // Parseamos la respuesta como un JSON Object
	        JsonObject accountData = JsonParser.parseString(response).getAsJsonObject();
	        if (accountData == null || !accountData.isJsonObject()) {
	            return "{\"error\": \"Error al analizar la respuesta de Spot Wallet.\"}";
	        }

	        // Verificar que la clave "balances" exista y que sea válida
	        if (accountData.has("balances") && accountData.get("balances").isJsonArray()) {
	            JsonArray balances = accountData.getAsJsonArray("balances");
	            double totalBalance = 0;
	            for (JsonElement element : balances) {
	                JsonObject balance = element.getAsJsonObject();

	                double freeBalance = getJsonDouble(balance, "free");
	                double lockedBalance = getJsonDouble(balance, "locked");

	                // Sumar balance disponible y bloqueado para obtener el balance total
	                totalBalance += freeBalance + lockedBalance;
	            }

	            return "{\"totalBalance\": " + totalBalance + "}";
	        } else {
	            return "{\"error\": \"La clave 'balances' no se encuentra en la respuesta.\"}";
	        }

	    } catch (Exception e) {
	        return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
	    }
	}





	private String getFuturesBalance(String account) {
	    try {
	        String[] credentials = getApiCredentials(account);
	        if (credentials == null) return "{\"error\": \"Cuenta no válida.\"}";

	        String apiKey = credentials[0];
	        String secretKey = credentials[1];

	        long timestamp = getServerTime();
	        String query = "timestamp=" + timestamp + "&recvWindow=60000";
	        String signature = hmacSha256(secretKey, query);
	        String url = "https://fapi.binance.com/fapi/v2/balance?" + query + "&signature=" + signature;

	        String response = sendBinanceRequestWithProxy(url, apiKey);

	        // Verificar si la respuesta es válida y no nula
	        if (response == null || response.isEmpty()) {
	            return "{\"error\": \"La respuesta de Futures Wallet está vacía o nula.\"}";
	        }

	        // Imprimir la respuesta para depuración
	        System.out.println("Response Futures Wallet: " + response);

	        // Parseamos la respuesta como un JSON Array
	        JsonArray balanceArray = JsonParser.parseString(response).getAsJsonArray();
	        if (balanceArray == null || balanceArray.size() == 0) {
	            return "{\"error\": \"La respuesta de Futures Wallet está vacía.\"}";
	        }

	        double totalBalance = 0;
	        for (JsonElement element : balanceArray) {
	            JsonObject balance = element.getAsJsonObject();
	            double availableBalance = balance.has("availableBalance") ? balance.get("availableBalance").getAsDouble() : 0;
	            double balanceTotal = balance.has("balance") ? balance.get("balance").getAsDouble() : 0;

	            // Sumar balance total, disponible
	            totalBalance += balanceTotal + availableBalance;
	        }

	        return "{\"totalBalance\": " + totalBalance + "}";

	    } catch (Exception e) {
	        return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
	    }
	}


	private double calculateTotalBalance(JsonObject balanceData) {
	    double total = 0;

	    // Si el balanceData contiene una lista de balances de spot o futuros, sumamos todos
	    if (balanceData.has("balances")) {
	        JsonArray balances = balanceData.getAsJsonArray("balances");
	        for (int i = 0; i < balances.size(); i++) {
	            JsonObject balance = balances.get(i).getAsJsonObject();
	            double freeBalance = balance.get("free").getAsDouble();
	            double lockedBalance = balance.get("locked").getAsDouble();

	            // Sumar balance libre y bloqueado
	            total += freeBalance + lockedBalance;
	        }
	    } else if (balanceData.has("totalWalletBalance")) {
	        // En el caso de futuros, obtenemos el balance total de la wallet
	        total = balanceData.get("totalWalletBalance").getAsDouble();
	    }

	    return total;
	}



	
	
	
	
	
	
	
	
	
	
	
	public Double getEstimatedSpotBalance(String account) {
	    try {
	        // 1) Credenciales
	        String[] creds = getApiCredentials(account);
	        if (creds == null) throw new RuntimeException("Cuenta no válida");
	        String apiKey = creds[0], secret = creds[1];

	        // 2) Obtengo balances
	        long ts = getServerTime();
	        String q = "timestamp=" + ts + "&recvWindow=60000";
	        String sig = hmacSha256(secret, q);
	        String accUrl = "https://api.binance.com/api/v3/account?" + q + "&signature=" + sig;
	        String rawAcc = sendBinanceRequestWithProxy(accUrl, apiKey);
	        JsonArray balances = JsonParser
	            .parseString(rawAcc)
	            .getAsJsonObject()
	            .getAsJsonArray("balances");

	        // 3) Obtengo todos los precios USDT de golpe
	        Map<String, Double> priceMap = fetchAllPriceUsdt();

	        // 4) Sumo solo activos que valgan >= 1 USD
	        double totalUsdt = 0.0;
	        for (JsonElement el : balances) {
	            JsonObject b = el.getAsJsonObject();
	            double free   = b.get("free").getAsDouble();
	            double locked = b.get("locked").getAsDouble();
	            double qty    = free + locked;
	            if (qty <= 0) continue;

	            String asset = b.get("asset").getAsString();
	            Double price = priceMap.get(asset);
	            if (price == null) {
	                // activo sin par USDT
	                continue;
	            }
	            double valUsd = qty * price;
	            if (valUsd < 1.0) {
	                // imita “Ocultar activos inferiores a 1 USD”
	                continue;
	            }
	            totalUsdt += valUsd;
	        }

	        return totalUsdt;
	    } catch (Exception e) {
	        throw new RuntimeException("Error al calcular balance estimado: " + e.getMessage(), e);
	    }
	}


    /** 
     * Consulta el precio de mercado de asset en USDT.
     * Para USDT devuelve 1.0 directamente.
     */
	private double getPriceInUsdt(String asset) {
	    if ("USDT".equalsIgnoreCase(asset)) {
	        return 1.0;
	    }
	    String symbol = asset + "USDT";
	    String url = "https://api.binance.com/api/v3/ticker/price?symbol=" + symbol;
	    try {
	        String resp = sendBinanceRequestWithProxy(url, null);
	        JsonObject obj = JsonParser.parseString(resp).getAsJsonObject();
	        return obj.get("price").getAsDouble();
	    } catch (RuntimeException re) {
	        // Si devuelve 400 o cualquier fallo, lo atrapamos y retornamos 0 para continuar
	        System.out.println("⚠️ No pude obtener precio de " + symbol + ": " + re.getMessage());
	        return 0.0;
	    } catch (Exception e) {
	        throw new RuntimeException("Error inesperado al pedir precio " + symbol, e);
	    }
	}
	
	/**
	 * Llama a GET /api/v3/ticker/price (sin symbol) y construye un Map:
	 *    key = símbolo base (ej. "BTC", "LINK", …)
	 *    value = precio en USDT
	 */
	private Map<String, Double> fetchAllPriceUsdt() throws Exception {
	    String url = "https://api.binance.com/api/v3/ticker/price";
	    String resp = sendBinanceRequestWithProxy(url, null);
	    JsonArray tickers = JsonParser.parseString(resp).getAsJsonArray();

	    Map<String, Double> priceMap = new HashMap<>();
	    for (JsonElement el : tickers) {
	        JsonObject o = el.getAsJsonObject();
	        String sym = o.get("symbol").getAsString();
	        if (sym.endsWith("USDT")) {
	            String asset = sym.substring(0, sym.length() - 4);
	            double price = o.get("price").getAsDouble();
	            priceMap.put(asset, price);
	        }
	    }
	    // Aseguramos que USDT → 1.0
	    priceMap.put("USDT", 1.0);
	    return priceMap;
	}
	
	public double getFundingAssetBalance(String account, String asset) {
	    try {
	        String[] creds = getApiCredentials(account);
	        if (creds == null) throw new RuntimeException("Cuenta no válida");
	        String apiKey    = creds[0];
	        String secretKey = creds[1];

	        long ts = getServerTime();
	        String params    = "asset=" + asset + "&timestamp=" + ts + "&recvWindow=60000";
	        String signature = hmacSha256(secretKey, params);

	        URL url = new URL("https://api.binance.com/sapi/v1/asset/get-funding-asset");
	        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	        conn.setRequestMethod("POST");
	        conn.setDoOutput(true);
	        conn.setRequestProperty("X-MBX-APIKEY", apiKey);
	        try (OutputStream os = conn.getOutputStream()) {
	            os.write((params + "&signature=" + signature).getBytes(StandardCharsets.UTF_8));
	        }

	        if (conn.getResponseCode() != 200) {
	            throw new RuntimeException("Error HTTP Funding Wallet: " + conn.getResponseCode());
	        }

	        String resp = new BufferedReader(new InputStreamReader(conn.getInputStream()))
	                          .lines().collect(Collectors.joining());
	        JsonArray arr = JsonParser.parseString(resp).getAsJsonArray();

	        for (JsonElement el : arr) {
	            JsonObject o = el.getAsJsonObject();
	            if (asset.equalsIgnoreCase(o.get("asset").getAsString())) {
	                double free   = o.get("free").getAsDouble();
	                // aquí leo "freeze" en vez de "locked"
	                double freeze = o.has("freeze")
	                                ? o.get("freeze").getAsDouble()
	                                : 0d;
	                return free + freeze;
	            }
	        }
	        return 0.0;
	    } catch (Exception e) {
	        throw new RuntimeException("Error al obtener Funding balance de " + asset + ": " + e.getMessage(), e);
	    }
	}





}
