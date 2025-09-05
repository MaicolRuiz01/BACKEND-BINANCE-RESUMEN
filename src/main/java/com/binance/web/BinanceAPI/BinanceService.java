package com.binance.web.BinanceAPI;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Repository.AccountBinanceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BinanceService {

	private static final String PAYMENTS_API_URL = "https://api.binance.com/sapi/v1/pay/transactions";
	private static final String P2P_ORDERS_API_URL = "https://api.binance.com/sapi/v1/c2c/orderMatch/listUserOrderHistory";
	@Autowired
	private AccountBinanceRepository accountRepo;

	// Claves API de cada cuenta
	private final String[][] apiKeys = {
			{ "MILTON", "EfBN9mFWAxk7CwsZzu37sXIGXyIQnyLVrAs3aqZOLAa3NumayunaGRQIJ6fi4U2r",
					"NbdiovuQxwgzwANxgZC669Jke5MZJUH3hyLT6BD8iWYz91EVK6e9adOY2Wq4t6nK" },
			{ "CESAR", "Ho474mufN8vTwvrZLjj8DdZHxa88JYlCrcPHp1r7UAhwc197So9vmUG9tRhM3XNr",
					"Ns41sTlvAM3nUzD0qMPE4PW57omuSxOPKdcngudgqVPphExjJC3tWX8kcxwibXDz" },
			{ "MARCEL", "vtNXEFCDEYxWpGGipXG210zzq5i2FnJAqmK5LJtRGiq5NRMCJqCQEOcR85SAunUP",
					"J9eIUXMxwFggHvU2HHp2EiWfNaXGvShSx5UihepHmW1gIjIBe3waZC3JvMUPBfga" },
			{ "SONIA", "N0lUyNy3rlgNxq6XKlKdjxVLppvBwPl1Bxi7FeDZ82G7X47oL2tor20vprJaLZLk",
					"Nqhxi7XMzNmQMk4phC442bkA368L8Toi0EAidGOJhal2f72olp5FMhOY7OoaehUg" },
			{ "BERNANDA", "UxPVjnvpZBgKgxHV6Qbds15TlTtFrBgyOycsw1Enj2ybiZFDc6ewk51ys3Sxvgxm",
					"GCliNB78z1FJkx5542QeY3PXsUBqJJMPQNZ6MmKAeEUjhItMLIhwKNhUw6pSCH8E" },
			{ "RIK", "vMSWVD5tca6o73oCOLcxefK0W4FA30nwUvAFhrRlpEfmOyX1Jv6Y94llAYJJT9HU",
					"nknvsAdsO1BWApJ7119jJKWTHWbT2HCGP0PJNrXsFiRR8GlWxHqmt25DyhNxu7Gw" },
			{ "JULIO", "M2GxXbjasdJPx6oWXhr3aKoeUB6WnfhFDZ02RmdTEhJlFkWMZkWqUCgfynM5iakd",
					"CDOTuuPusPaZSZakDmISILjjmSNfW6v9A3QGqdh7F43OwD7MT5LQgJowto2VFBlt" } };

	public List<String> getAllAccountNames() {
		List<String> dbAccounts = accountRepo.findByTipo("BINANCE").stream()
				.filter(a -> a.getApiKey() != null && a.getApiSecret() != null).map(AccountBinance::getName)
				.collect(Collectors.toList());

		List<String> staticAccounts = Arrays.stream(apiKeys).map(keys -> keys[0])
				.filter(name -> dbAccounts.stream().noneMatch(dbName -> dbName.equals(name)))
				.collect(Collectors.toList());

		dbAccounts.addAll(staticAccounts);
		return dbAccounts;
	}

	// metodo para obtener los movimientos de binancepay las que se hacen por correo
	public String getPaymentHistory(String account) {
		try {
			String[] credentials = getApiCredentials(account);
			if (credentials == null)
				return "{\"error\": \"Cuenta no válida.\"}";

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
			if (credentials == null)
				return "{\"error\": \"Cuenta no válida.\"}";

			String apiKey = credentials[0];
			String secretKey = credentials[1];

			List<JsonObject> allOrders = new ArrayList<>();
			int currentPage = 1;
			int rows = 50;

			while (true) {
				long timestamp = getServerTime();
				String query = "tradeType=SELL" + "&startTimestamp=" + startTime + "&endTimestamp=" + endTime + "&page="
						+ currentPage + "&rows=" + rows + "&recvWindow=60000" + "&timestamp=" + timestamp;

				String signature = hmacSha256(secretKey, query);
				String url = P2P_ORDERS_API_URL + "?" + query + "&signature=" + signature;

				String response = sendBinanceRequestWithProxy(url, apiKey);
				JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

				if (jsonResponse.has("code") && !jsonResponse.get("code").getAsString().equals("000000")) {
					return "{\"error\": \"" + jsonResponse.get("msg").getAsString() + "\"}";
				}

				JsonArray dataArray = jsonResponse.getAsJsonArray("data");
				if (dataArray == null || dataArray.size() == 0)
					break;

				dataArray.forEach(order -> allOrders.add(order.getAsJsonObject()));

				int totalOrders = jsonResponse.has("total") ? jsonResponse.get("total").getAsInt() : 0;
				if (allOrders.size() >= totalOrders)
					break;

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

	private String[] getApiCredentials(String accountName) {
		AccountBinance account = accountRepo.findByName(accountName);
		if (account != null && "BINANCE".equalsIgnoreCase(account.getTipo())) {
			if (account.getApiKey() != null && account.getApiSecret() != null) {
				return new String[] { account.getApiKey(), account.getApiSecret() };
			}
		}

		// Si no está en DB o es de tipo diferente, usa los quemados
		for (String[] key : apiKeys) {
			if (key[0].equals(accountName)) {
				return new String[] { key[1], key[2] };
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
		LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));
		long startTime = today.atStartOfDay(ZoneId.of("America/Bogota")).toInstant().toEpochMilli();
		long endTime = today.plusDays(1).atStartOfDay(ZoneId.of("America/Bogota")).toInstant().toEpochMilli();
		return getP2POrdersInRange(account, startTime, endTime);
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
			if (credentials == null)
				return "{\"error\": \"Cuenta no válida.\"}";

			String apiKey = credentials[0];
			String secretKey = credentials[1];

			long timestamp = getServerTime();
			String query = "symbol=" + symbol + "&limit=" + limit + "&timestamp=" + timestamp + "&recvWindow=60000";

			String signature = hmacSha256(secretKey, query);
			String url = "https://api.binance.com/api/v3/allOrders?" + query + "&signature=" + signature;

			return sendBinanceRequestWithProxy(url, apiKey);

		} catch (Exception e) {
			return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
		}
	}

	// estp es para futures
	public String getFuturesOrders(String account, String symbol, int limit) {
		try {
			String[] credentials = getApiCredentials(account);
			if (credentials == null)
				return "{\"error\": \"Cuenta no válida.\"}";

			String apiKey = credentials[0];
			String secretKey = credentials[1];

			long timestamp = getServerTime();
			String query = "symbol=" + symbol + "&limit=" + limit + "&timestamp=" + timestamp + "&recvWindow=60000";

			String signature = hmacSha256(secretKey, query);
			String url = "https://fapi.binance.com/fapi/v1/allOrders?" + query + "&signature=" + signature;

			return sendBinanceRequestWithProxy(url, apiKey);

		} catch (Exception e) {
			return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
		}
	}

	// estp es para futures
	public String getFuturesPositions(String account) {
		try {
			String[] credentials = getApiCredentials(account);
			if (credentials == null)
				return "{\"error\": \"Cuenta no válida.\"}";

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
			if (credentials == null)
				return "{\"error\": \"Cuenta no válida.\"}";

			String apiKey = credentials[0];
			String secretKey = credentials[1];

			long timestamp = getServerTime();
			String query = "limit=" + limit + "&timestamp=" + timestamp + "&recvWindow=60000";

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
			if (credentials == null)
				return "{\"error\": \"Cuenta no válida.\"}";

			String apiKey = credentials[0];
			String secretKey = credentials[1];

			long timestamp = getServerTime();
			String query = "limit=" + limit + "&timestamp=" + timestamp + "&recvWindow=60000";

			String signature = hmacSha256(secretKey, query);
			String url = "https://api.binance.com/sapi/v1/capital/withdraw/history?" + query + "&signature="
					+ signature;

			return sendBinanceRequestWithProxy(url, apiKey);

		} catch (Exception e) {
			return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
		}
	}

	public String getSpotBalanceByAsset(String account, String asset) {
		try {
			String[] credentials = getApiCredentials(account);
			if (credentials == null)
				return "{\"error\": \"Cuenta no válida.\"}";

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

	// este metodo busca el valor de los trx en una fecha que le mandamos, esto lo
	// uso para pasar los trx a usdt
	// en traspasos y ventas, podemos ver en el spotcontroller que se usa este
	// metodo

	public Double getHistoricalPriceTRXUSDT(LocalDateTime fecha) {
		try {
			long startTime = fecha.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();
			long endTime = startTime + 60000; // +1 minuto

			String url = "https://api.binance.com/api/v3/klines?symbol=TRXUSDT&interval=1m&startTime=" + startTime
					+ "&endTime=" + endTime;

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

	// Método auxiliar para manejar conversiones de valores de JSON a Double con
	// validación
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
			if (credentials == null)
				return "{\"error\": \"Cuenta no válida.\"}";

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
			if (credentials == null)
				return "{\"error\": \"Cuenta no válida.\"}";

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
				double availableBalance = balance.has("availableBalance")
						? balance.get("availableBalance").getAsDouble()
						: 0;
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

		// Si el balanceData contiene una lista de balances de spot o futuros, sumamos
		// todos
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
			if (creds == null)
				throw new RuntimeException("Cuenta no válida");
			String apiKey = creds[0], secret = creds[1];

			// 2) Obtengo balances
			long ts = getServerTime();
			String q = "timestamp=" + ts + "&recvWindow=60000";
			String sig = hmacSha256(secret, q);
			String accUrl = "https://api.binance.com/api/v3/account?" + q + "&signature=" + sig;
			String rawAcc = sendBinanceRequestWithProxy(accUrl, apiKey);
			JsonArray balances = JsonParser.parseString(rawAcc).getAsJsonObject().getAsJsonArray("balances");

			// 3) Obtengo todos los precios USDT de golpe
			Map<String, Double> priceMap = fetchAllPriceUsdt();

			// 4) Sumo solo activos que valgan >= 1 USD
			double totalUsdt = 0.0;
			for (JsonElement el : balances) {
				JsonObject b = el.getAsJsonObject();
				double free = b.get("free").getAsDouble();
				double locked = b.get("locked").getAsDouble();
				double qty = free + locked;
				if (qty <= 0)
					continue;

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
	 * Consulta el precio de mercado de asset en USDT. Para USDT devuelve 1.0
	 * directamente.
	 */
	public double getPriceInUsdt(String asset) {
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
	 * Llama a GET /api/v3/ticker/price (sin symbol) y construye un Map: key =
	 * símbolo base (ej. "BTC", "LINK", …) value = precio en USDT
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
			if (creds == null)
				throw new RuntimeException("Cuenta no válida");
			String apiKey = creds[0];
			String secretKey = creds[1];

			long ts = getServerTime();
			String params = "asset=" + asset + "&timestamp=" + ts + "&recvWindow=60000";
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

			String resp = new BufferedReader(new InputStreamReader(conn.getInputStream())).lines()
					.collect(Collectors.joining());
			JsonArray arr = JsonParser.parseString(resp).getAsJsonArray();

			for (JsonElement el : arr) {
				JsonObject o = el.getAsJsonObject();
				if (asset.equalsIgnoreCase(o.get("asset").getAsString())) {
					double free = o.get("free").getAsDouble();
					// aquí leo "freeze" en vez de "locked"
					double freeze = o.has("freeze") ? o.get("freeze").getAsDouble() : 0d;
					return free + freeze;
				}
			}
			return 0.0;
		} catch (Exception e) {
			throw new RuntimeException("Error al obtener Funding balance de " + asset + ": " + e.getMessage(), e);
		}
	}

	private List<AnuncioDto> obtenerAnunciosPorTipo(Map<String, Object> filtros, String tipo, String cuenta) {
		String url = "https://p2p.binance.com/bapi/c2c/v2/friendly/c2c/adv/search";
		RestTemplate restTemplate = new RestTemplate();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Accept-Encoding", "identity");

		Map<String, Object> requestBody = new HashMap<>();
		requestBody.put("asset", filtros.getOrDefault("asset", "USDT"));
		requestBody.put("fiat", filtros.getOrDefault("fiat", "COP"));
		requestBody.put("tradeType", tipo);
		requestBody.put("payTypes", filtros.getOrDefault("payTypes", List.of()));
		requestBody.put("page", filtros.getOrDefault("page", 1));
		requestBody.put("rows", filtros.getOrDefault("rows", 10));
		requestBody.put("publisherType", filtros.getOrDefault("publisherType", null));

		HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
		ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

		List<AnuncioDto> anunciosFiltrados = new ArrayList<>();

		try {
			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode root = objectMapper.readTree(response.getBody());
			JsonNode data = root.path("data");

			if (data.isArray()) {
				for (JsonNode item : data) {
					JsonNode adv = item.path("adv");
					JsonNode advertiser = item.path("advertiser");

					AnuncioDto anuncio = new AnuncioDto();
					anuncio.setCuenta(cuenta);
					anuncio.setPrecio(adv.path("price").asText(""));
					anuncio.setMoneda(adv.path("asset").asText(""));
					anuncio.setFiat(adv.path("fiatUnit").asText(""));
					anuncio.setMinimo(adv.path("minSingleTransAmount").asText(""));
					anuncio.setMaximo(adv.path("maxSingleTransAmount").asText(""));

					JsonNode tradeMethods = adv.path("tradeMethods");
					anuncio.setMetodoPago(tradeMethods.isArray() && tradeMethods.size() > 0
							? tradeMethods.get(0).path("tradeMethodName").asText("")
							: "");

					anuncio.setVendedor(advertiser.path("nickName").asText(""));
					anuncio.setTipo(adv.path("tradeType").asText(""));

					long createTimeMillis = adv.path("createTime").asLong(0);
					ZonedDateTime dateTime = Instant.ofEpochMilli(createTimeMillis).atZone(ZoneId.of("America/Bogota"));
					String horaAnuncio = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
					anuncio.setHoraAnuncio(horaAnuncio);

					anunciosFiltrados.add(anuncio);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return anunciosFiltrados;
	}

	public List<AnuncioDto> obtenerTodosLosAnuncios(Map<String, Object> filtros) {
		List<AnuncioDto> anuncios = new ArrayList<>();
		List<String> cuentas = getAllAccountNames();

		for (String cuenta : cuentas) {
			anuncios.addAll(obtenerAnunciosPorTipo(filtros, "BUY", cuenta));
			anuncios.addAll(obtenerAnunciosPorTipo(filtros, "SELL", cuenta));
		}

		return anuncios;
	}

	public Double getGeneralBalanceInUSDT(String account) {
		try {
			String[] creds = getApiCredentials(account);
			if (creds == null)
				throw new RuntimeException("Cuenta no válida");
			String apiKey = creds[0], secret = creds[1];

			Map<String, Double> priceMap = fetchAllPriceUsdt();
			double totalUsdt = 0.0;

			long ts = getServerTime();
			String q = "timestamp=" + ts + "&recvWindow=60000";
			String sig = hmacSha256(secret, q);

			// 🔸 Spot Wallet
			try {
				String spotUrl = "https://api.binance.com/api/v3/account?" + q + "&signature=" + sig;
				String spotRaw = sendBinanceRequestWithProxy(spotUrl, apiKey);
				JsonArray spotBalances = JsonParser.parseString(spotRaw).getAsJsonObject().getAsJsonArray("balances");

				for (JsonElement el : spotBalances) {
					JsonObject b = el.getAsJsonObject();
					double qty = b.get("free").getAsDouble() + b.get("locked").getAsDouble();
					if (qty <= 0)
						continue;

					String asset = b.get("asset").getAsString();
					Double price = priceMap.get(asset);
					if (price == null)
						continue;
					totalUsdt += qty * price;
				}
			} catch (Exception ex) {
				System.out.println("⚠️ Error Spot: " + ex.getMessage());
			}

			// 🔸 Futures Wallet
			try {
				String futUrl = "https://fapi.binance.com/fapi/v2/balance?" + q + "&signature=" + hmacSha256(secret, q);
				String futRaw = sendBinanceRequestWithProxy(futUrl, apiKey);
				JsonArray futArray = JsonParser.parseString(futRaw).getAsJsonArray();

				for (JsonElement el : futArray) {
					JsonObject b = el.getAsJsonObject();
					String asset = b.get("asset").getAsString();
					double qty = b.get("balance").getAsDouble();
					Double price = priceMap.get(asset);
					if (price == null)
						continue;
					totalUsdt += qty * price;
				}
			} catch (Exception ex) {
				System.out.println("⚠️ Error Futures: " + ex.getMessage());
			}

			// 🔸 Funding Wallet
			try {
				totalUsdt += getFundingAssetBalance(account, "USDT");
			} catch (Exception ex) {
				System.out.println("⚠️ Error Funding: " + ex.getMessage());
			}

			return totalUsdt;

		} catch (Exception e) {
			throw new RuntimeException("Error general en cálculo de balance USDT: " + e.getMessage(), e);
		}
	}

	public BigDecimal calcularComisionUSDTDesdeHash(String txHash) {
		try {
			// 1. Consultar transacción en TronScan
			String tronScanUrl = "https://apilist.tronscanapi.com/api/transaction-info?hash=" + txHash;
			RestTemplate restTemplate = new RestTemplate();
			String tronResponse = restTemplate.getForObject(tronScanUrl, String.class);

			JsonObject tronJson = JsonParser.parseString(tronResponse).getAsJsonObject();

			// 2. Obtener fee en SUN y timestamp
			long feeSun = tronJson.get("cost").getAsJsonObject().get("net_fee").getAsLong(); // o energy_fee según el
																								// caso
			long timestampMillis = tronJson.get("timestamp").getAsLong();

			// 3. Convertir SUN → TRX
			BigDecimal feeTRX = new BigDecimal(feeSun).divide(BigDecimal.valueOf(1_000_000));

			// 4. Obtener precio histórico TRX/USDT en ese minuto
			long startTime = timestampMillis - (timestampMillis % 60000);
			String binanceUrl = String.format(
					"https://api.binance.com/api/v3/klines?symbol=TRXUSDT&interval=1m&startTime=%d&endTime=%d&limit=1",
					startTime, startTime + 60000);

			String binanceResponse = restTemplate.getForObject(binanceUrl, String.class);
			JsonArray vela = JsonParser.parseString(binanceResponse).getAsJsonArray();
			if (vela.size() == 0) {
				throw new RuntimeException("No se encontró precio para ese momento");
			}

			String precioCierreStr = vela.get(0).getAsJsonArray().get(4).getAsString();
			BigDecimal precioTRXUSDT = new BigDecimal(precioCierreStr);

			// 5. Calcular comisión en USDT
			return feeTRX.multiply(precioTRXUSDT);

		} catch (Exception e) {
			throw new RuntimeException("Error calculando comisión en USDT desde hash: " + e.getMessage(), e);
		}
	}

	// para obtener el hisorial de traider o hisorial de operaciones spot
	// Nuevo método para obtener todos los trades de spot para todas las cuentas
	public String getAllSpotTradesForAllAccounts(String symbol, int limit) {
		try {
			List<JsonObject> allTrades = new ArrayList<>();
			List<AccountBinance> accounts = accountRepo.findByTipo("BINANCE");

			for (AccountBinance account : accounts) {
				String accountName = account.getName();
				String apiKey = account.getApiKey();
				String secretKey = account.getApiSecret();

				if (apiKey == null || secretKey == null) {
					System.err.println("DEBUG: Saltando cuenta " + accountName + " por falta de credenciales.");
					continue;
				}

				// Llama al método para obtener los trades de spot para una cuenta específica y
				// un par
				String response = getSpotTrades(accountName, symbol, limit);

				// Procesa la respuesta
				JsonElement parsedResponse = JsonParser.parseString(response);
				if (parsedResponse.isJsonArray()) {
					JsonArray tradesArray = parsedResponse.getAsJsonArray();
					for (JsonElement tradeElement : tradesArray) {
						JsonObject trade = tradeElement.getAsJsonObject();
						trade.addProperty("account", accountName);
						allTrades.add(trade);
					}
				} else {
					System.err.println(
							"Error al obtener trades para " + accountName + " con " + symbol + ": " + response);
				}
			}

			return new Gson().toJson(allTrades);

		} catch (Exception e) {
			e.printStackTrace();
			return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
		}
	}

	// Método modificado para obtener trades de spot usando el endpoint correcto.
	// Se hace privado para que sea llamado solo internamente.
	// En BinanceService.java
	public String getSpotTrades(String account, String symbol, int limit) {
		try {
			String[] credentials = getApiCredentials(account);
			if (credentials == null) {
				return "{\"error\": \"Cuenta no válida.\"}";
			}

			String apiKey = credentials[0];
			String secretKey = credentials[1];

			long timestamp = getServerTime();
			// La cadena de consulta para la firma debe estar en el orden correcto
			String query = "symbol=" + symbol + "&limit=" + limit + "&timestamp=" + timestamp + "&recvWindow=60000";

			// Genera la firma usando la cadena de consulta
			String signature = hmacSha256(secretKey, query);

			// Construye la URL final con la firma
			String url = "https://api.binance.com/api/v3/myTrades?" + query + "&signature=" + signature;

			return sendBinanceRequestWithProxy(url, apiKey);

		} catch (Exception e) {
			return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
		}
	}

	// Método para obtener el historial de órdenes completadas o canceladas
	public String getOrderHistory(String apiKey, String secretKey, String symbol, int limit) throws Exception {
	    long ts = getServerTime();
	    String q = "symbol="+symbol+"&timestamp="+ts+"&recvWindow=60000&limit="+limit;
	    String sig = hmacSha256(secretKey, q);
	    String url = "https://api.binance.com/api/v3/allOrders?" + q + "&signature=" + sig;
	    return sendBinanceRequestWithProxy(url, apiKey);
	}
	
	public Double getPriceInUSDT(String asset) {
	    if (asset == null) return null;
	    String a = asset.trim().toUpperCase();
	    if ("USDT".equals(a) || "USDC".equals(a)) return 1.0;

	    String symbol = a + "USDT";
	    String url = "https://api.binance.com/api/v3/ticker/price?symbol=" + symbol;
	    try {
	        String resp = sendBinanceRequestWithProxy(url, null);
	        JsonObject obj = JsonParser.parseString(resp).getAsJsonObject();
	        return obj.get("price").getAsDouble();
	    } catch (RuntimeException re) {
	        System.out.println("⚠️ No pude obtener precio de " + symbol + ": " + re.getMessage());
	        return null; // el caller lo tratará como 0.0
	    } catch (Exception e) {
	        return null;
	    }
	}
	
	// BinanceService.java

	public Map<String, Double> getSpotBalancesMap(String account) throws Exception {
	    String[] creds = getApiCredentials(account);
	    if (creds == null) throw new RuntimeException("Cuenta no válida");
	    String apiKey = creds[0], secret = creds[1];

	    long ts = getServerTime();
	    String q = "timestamp=" + ts + "&recvWindow=60000";
	    String sig = hmacSha256(secret, q);
	    String url = "https://api.binance.com/api/v3/account?" + q + "&signature=" + sig;

	    String raw = sendBinanceRequestWithProxy(url, apiKey);
	    JsonArray balances = JsonParser.parseString(raw)
	            .getAsJsonObject().getAsJsonArray("balances");

	    Map<String, Double> map = new HashMap<>();
	    for (JsonElement el : balances) {
	        JsonObject b = el.getAsJsonObject();
	        double qty = b.get("free").getAsDouble() + b.get("locked").getAsDouble();
	        if (qty <= 0) continue;
	        String asset = b.get("asset").getAsString();
	        map.put(asset, qty);
	    }
	    return map;
	}

	/** Funding Wallet: si NO envías "asset", Binance devuelve solo activos con balance > 0 */
	public Map<String, Double> getFundingBalancesMap(String account) throws Exception {
	    String[] creds = getApiCredentials(account);
	    if (creds == null) throw new RuntimeException("Cuenta no válida");
	    String apiKey = creds[0], secret = creds[1];

	    long ts = getServerTime();
	    String params = "timestamp=" + ts + "&recvWindow=60000";
	    String signature = hmacSha256(secret, params);

	    URL url = new URL("https://api.binance.com/sapi/v1/asset/get-funding-asset");
	    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	    conn.setRequestMethod("POST");
	    conn.setDoOutput(true);
	    conn.setRequestProperty("X-MBX-APIKEY", apiKey);
	    try (OutputStream os = conn.getOutputStream()) {
	        os.write((params + "&signature=" + signature).getBytes(StandardCharsets.UTF_8));
	    }
	    if (conn.getResponseCode() != 200) {
	        throw new RuntimeException("HTTP Funding: " + conn.getResponseCode());
	    }
	    String resp = new BufferedReader(new InputStreamReader(conn.getInputStream()))
	            .lines().collect(Collectors.joining());

	    Map<String, Double> map = new HashMap<>();
	    JsonArray arr = JsonParser.parseString(resp).getAsJsonArray();
	    for (JsonElement el : arr) {
	        JsonObject o = el.getAsJsonObject();
	        String asset = o.get("asset").getAsString();
	        double free   = o.has("free")   ? o.get("free").getAsDouble()   : 0d;
	        double freeze = o.has("freeze") ? o.get("freeze").getAsDouble() : 0d;
	        double qty = free + freeze;
	        if (qty > 0) map.merge(asset, qty, Double::sum);
	    }
	    return map;
	}

	/** Spot + Funding (puedes extender a Futures si lo necesitas) */
	public Map<String, Double> getAllBalancesByAsset(String account) {
	    try {
	        Map<String, Double> spot = getSpotBalancesMap(account);
	        Map<String, Double> fund = getFundingBalancesMap(account);
	        // Merge
	        fund.forEach((k, v) -> spot.merge(k, v, Double::sum));
	        return spot; // contiene la suma final por símbolo
	    } catch (Exception e) {
	        throw new RuntimeException("Error obteniendo balances de " + account + ": " + e.getMessage(), e);
	    }
	}

	// En BinanceService.java (añade al final)

	public String getMyTradesByOrder(String apiKey, String secretKey, String symbol, long orderId) throws Exception {
	    long ts = getServerTime();
	    String q = "symbol="+symbol+"&orderId="+orderId+"&timestamp="+ts+"&recvWindow=60000";
	    String sig = hmacSha256(secretKey, q);
	    String url = "https://api.binance.com/api/v3/myTrades?" + q + "&signature=" + sig;
	    return sendBinanceRequestWithProxy(url, apiKey);
	}

	/** Activos con balance > 0 (spot) para inferir símbolos probables. */
	public Set<String> getNonZeroAssets(String account) {
	    try {
	        String[] creds = getApiCredentials(account);
	        if (creds == null) throw new RuntimeException("Cuenta no válida");
	        String apiKey = creds[0], secret = creds[1];

	        long ts = getServerTime();
	        String q = "timestamp=" + ts + "&recvWindow=60000";
	        String sig = hmacSha256(secret, q);
	        String url = "https://api.binance.com/api/v3/account?" + q + "&signature=" + sig;

	        String raw = sendBinanceRequestWithProxy(url, apiKey);
	        JsonArray balances = JsonParser.parseString(raw).getAsJsonObject().getAsJsonArray("balances");

	        Set<String> out = new HashSet<>();
	        for (JsonElement el : balances) {
	            JsonObject b = el.getAsJsonObject();
	            double qty = b.get("free").getAsDouble() + b.get("locked").getAsDouble();
	            if (qty > 0.0) out.add(b.get("asset").getAsString());
	        }
	        return out;
	    } catch (Exception e) {
	        throw new RuntimeException("Error al leer balances de " + account + ": " + e.getMessage(), e);
	    }
	}

	// En BinanceService.java
	public Set<String> getTradableSymbolsByQuotes(Collection<String> quotes) {
	    try {
	        String url = "https://api.binance.com/api/v3/exchangeInfo";
	        String resp = sendBinanceRequestWithProxy(url, null);
	        JsonObject root = JsonParser.parseString(resp).getAsJsonObject();
	        JsonArray syms = root.getAsJsonArray("symbols");

	        Set<String> out = new HashSet<>();
	        for (JsonElement el : syms) {
	            JsonObject s = el.getAsJsonObject();
	            if (!"TRADING".equalsIgnoreCase(s.get("status").getAsString())) continue;
	            String quote = s.get("quoteAsset").getAsString();
	            if (quotes.contains(quote)) {
	                out.add(s.get("symbol").getAsString()); // p.ej. TRXUSDT
	            }
	        }
	        return out;
	    } catch (Exception e) {
	        throw new RuntimeException("No pude cargar exchangeInfo: " + e.getMessage(), e);
	    }
	}




}
