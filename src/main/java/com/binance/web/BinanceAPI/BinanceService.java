package com.binance.web.BinanceAPI;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Repository.AccountBinanceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BinanceService {

	private static final String PAYMENTS_API_URL   = "https://api.binance.com/sapi/v1/pay/transactions";
	private static final String P2P_ORDERS_API_URL = "https://api.binance.com/sapi/v1/c2c/orderMatch/listUserOrderHistory";
	private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

	private final ObjectMapper mapper = new ObjectMapper();

	/** RestTemplate compartido — configura timeouts en HttpClientConfig si lo necesitas */
	private final RestTemplate restTemplate = new RestTemplate();

	@Autowired
	private AccountBinanceRepository accountRepo;

	// ─────────────────────────────────────────────────────────────
	// Helpers internos
	// ─────────────────────────────────────────────────────────────

	private String[] getApiCredentials(String accountName) {
		AccountBinance account = accountRepo.findByName(accountName);
		if (account != null && "BINANCE".equalsIgnoreCase(account.getTipo())
				&& account.getApiKey() != null && account.getApiSecret() != null) {
			return new String[]{account.getApiKey(), account.getApiSecret()};
		}
		return null;
	}

	private String hmacSha256(String secretKey, String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : hash) sb.append(String.format("%02x", b));
			return sb.toString();
		} catch (Exception e) {
			throw new RuntimeException("Error generando firma HMAC SHA256", e);
		}
	}

	/**
	 * Llamada GET autenticada a Binance (o pública si apiKey == null).
	 * Reemplaza el antiguo HttpURLConnection manual.
	 */
	private String binanceGet(String url, String apiKey) {
		HttpHeaders headers = new HttpHeaders();
		if (apiKey != null) headers.set("X-MBX-APIKEY", apiKey);
		HttpEntity<Void> entity = new HttpEntity<>(headers);
		try {
			return restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
		} catch (HttpClientErrorException ex) {
			boolean isFutures = url.contains("fapi.binance.com");
			if (isFutures && (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.FORBIDDEN)) {
				log.warn("Futures sin permiso ({}) para URL: {}", ex.getStatusCode(), url);
				return "[]";
			}
			throw new RuntimeException("Error HTTP " + ex.getStatusCode() + " — " + ex.getResponseBodyAsString(), ex);
		}
	}

	/**
	 * Llamada POST autenticada (usada para Funding wallet).
	 * El body se envía como form-urlencoded.
	 */
	private String binancePost(String url, String apiKey, String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-MBX-APIKEY", apiKey);
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		HttpEntity<String> entity = new HttpEntity<>(body, headers);
		try {
			return restTemplate.exchange(url, HttpMethod.POST, entity, String.class).getBody();
		} catch (HttpClientErrorException ex) {
			throw new RuntimeException("Error HTTP POST " + ex.getStatusCode() + " — " + ex.getResponseBodyAsString(), ex);
		}
	}

	private long getServerTime() throws Exception {
		String response = binanceGet("https://api.binance.com/api/v3/time", null);
		JsonNode root = mapper.readTree(response);
		if (!root.has("serverTime") || root.get("serverTime").isNull()) {
			throw new RuntimeException("No se pudo obtener el timestamp del servidor Binance");
		}
		return root.get("serverTime").asLong();
	}

	// ─────────────────────────────────────────────────────────────
	// Cuentas
	// ─────────────────────────────────────────────────────────────

	public List<String> getAllAccountNames() {
		return accountRepo.findByTipo("BINANCE").stream()
				.filter(a -> a.getApiKey() != null && a.getApiSecret() != null)
				.map(AccountBinance::getName)
				.toList();
	}

	// ─────────────────────────────────────────────────────────────
	// BinancePay
	// ─────────────────────────────────────────────────────────────

	/** Caché de 120 s por cuenta — BinancePay no cambia en tiempo real */
	@Cacheable(value = "binancePayHistory", key = "#account")
	public String getPaymentHistory(String account) {
		try {
			String[] creds = getApiCredentials(account);
			if (creds == null) return "{\"error\": \"Cuenta no válida.\"}";
			long ts = getServerTime();
			String query = "timestamp=" + ts + "&recvWindow=60000";
			String sig   = hmacSha256(creds[1], query);
			return binanceGet(PAYMENTS_API_URL + "?" + query + "&signature=" + sig, creds[0]);
		} catch (Exception e) {
			try {
				return mapper.writeValueAsString(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error interno"));
			} catch (Exception ex) {
				return "{\"error\": \"Error interno\"}";
			}
		}
	}

	// ─────────────────────────────────────────────────────────────
	// P2P
	// ─────────────────────────────────────────────────────────────

	/**
	 * Obtiene órdenes P2P en un rango de fechas.
	 * tradeType puede ser "BUY", "SELL" o null para todos.
	 * Reemplaza y elimina el overload antiguo sin tradeType.
	 */
	public String getP2POrdersInRange(String account, long startTime, long endTime, String tradeType) {
		try {
			String[] creds = getApiCredentials(account);
			if (creds == null) return "{\"error\": \"Cuenta no válida.\"}";

			ArrayNode allOrders = mapper.createArrayNode();
			int page = 1;
			int rows = 50;

			while (true) {
				long ts = getServerTime();
				StringBuilder query = new StringBuilder();
				if (tradeType != null && !tradeType.isBlank()) {
					query.append("tradeType=").append(tradeType).append("&");
				}
				query.append("startTimestamp=").append(startTime)
					 .append("&endTimestamp=").append(endTime)
					 .append("&page=").append(page)
					 .append("&rows=").append(rows)
					 .append("&recvWindow=60000")
					 .append("&timestamp=").append(ts);

				String sig      = hmacSha256(creds[1], query.toString());
				String response = binanceGet(P2P_ORDERS_API_URL + "?" + query + "&signature=" + sig, creds[0]);
				JsonNode root   = mapper.readTree(response);

				if (root.has("code") && !"000000".equals(root.get("code").asText())) {
					return "{\"error\": \"" + root.path("msg").asText() + "\"}";
				}

				JsonNode dataArray = root.path("data");
				if (!dataArray.isArray() || dataArray.size() == 0) break;

				dataArray.forEach(allOrders::add);

				int total = root.path("total").asInt(0);
				if (allOrders.size() >= total) break;
				page++;
			}

			ObjectNode finalResponse = mapper.createObjectNode();
			finalResponse.set("data", allOrders);
			return mapper.writeValueAsString(finalResponse);

		} catch (Exception e) {
			log.warn("[P2PSync] API error en cuenta '{}': {}", account, e.getMessage());
			try {
				return mapper.writeValueAsString(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error interno"));
			} catch (Exception ex) {
				return "{\"error\": \"Error interno\"}";
			}
		}
	}

	/** Obtiene órdenes P2P del día actual (ambos tipos). */
	public String getP2POrderLatest(String account) {
		LocalDate today = LocalDate.now(ZONE_BOGOTA);
		long startTime  = today.atStartOfDay(ZONE_BOGOTA).toInstant().toEpochMilli();
		long endTime    = today.plusDays(1).atStartOfDay(ZONE_BOGOTA).toInstant().toEpochMilli();
		return getP2POrdersInRange(account, startTime, endTime, null);
	}

	// ─────────────────────────────────────────────────────────────
	// Futures
	// ─────────────────────────────────────────────────────────────

	public String getFuturesOrders(String account, String symbol, int limit) {
		try {
			String[] creds = getApiCredentials(account);
			if (creds == null) return "{\"error\": \"Cuenta no válida.\"}";
			long ts  = getServerTime();
			String q = "symbol=" + symbol + "&limit=" + limit + "&timestamp=" + ts + "&recvWindow=60000";
			return binanceGet("https://fapi.binance.com/fapi/v1/allOrders?" + q + "&signature=" + hmacSha256(creds[1], q), creds[0]);
		} catch (Exception e) {
			return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
		}
	}

	public String getFuturesPositions(String account) {
		try {
			String[] creds = getApiCredentials(account);
			if (creds == null) return "{\"error\": \"Cuenta no válida.\"}";
			long ts  = getServerTime();
			String q = "timestamp=" + ts + "&recvWindow=60000";
			return binanceGet("https://fapi.binance.com/fapi/v2/positionRisk?" + q + "&signature=" + hmacSha256(creds[1], q), creds[0]);
		} catch (Exception e) {
			return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
		}
	}

	// ─────────────────────────────────────────────────────────────
	// Spot
	// ─────────────────────────────────────────────────────────────

	public String getSpotDeposits(String account, int limit) {
		try {
			String[] creds = getApiCredentials(account);
			if (creds == null) return "{\"error\": \"Cuenta no válida.\"}";
			long ts  = getServerTime();
			String q = "limit=" + limit + "&timestamp=" + ts + "&recvWindow=60000";
			return binanceGet("https://api.binance.com/sapi/v1/capital/deposit/hisrec?" + q + "&signature=" + hmacSha256(creds[1], q), creds[0]);
		} catch (Exception e) {
			return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
		}
	}

	public String getSpotWithdrawals(String account, int limit) {
		try {
			String[] creds = getApiCredentials(account);
			if (creds == null) return "{\"error\": \"Cuenta no válida.\"}";
			long ts  = getServerTime();
			String q = "limit=" + limit + "&timestamp=" + ts + "&recvWindow=60000";
			return binanceGet("https://api.binance.com/sapi/v1/capital/withdraw/history?" + q + "&signature=" + hmacSha256(creds[1], q), creds[0]);
		} catch (Exception e) {
			return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
		}
	}

	public Map<String, Double> getSpotBalancesMap(String account) throws Exception {
		String[] creds = getApiCredentials(account);
		if (creds == null) throw new RuntimeException("Cuenta no válida");
		long ts  = getServerTime();
		String q = "timestamp=" + ts + "&recvWindow=60000";
		String raw = binanceGet("https://api.binance.com/api/v3/account?" + q + "&signature=" + hmacSha256(creds[1], q), creds[0]);

		Map<String, Double> map = new HashMap<>();
		for (JsonNode b : mapper.readTree(raw).path("balances")) {
			double qty = b.path("free").asDouble() + b.path("locked").asDouble();
			if (qty > 0) map.put(b.path("asset").asText(), qty);
		}
		return map;
	}

	public Double getEstimatedSpotBalance(String account) {
		try {
			Map<String, Double> priceMap = fetchAllPriceUsdt();
			double total = 0.0;
			for (Map.Entry<String, Double> e : getSpotBalancesMap(account).entrySet()) {
				Double price = priceMap.get(e.getKey());
				if (price == null) continue;
				double val = e.getValue() * price;
				if (val >= 1.0) total += val;
			}
			return total;
		} catch (Exception e) {
			throw new RuntimeException("Error al calcular balance estimado: " + e.getMessage(), e);
		}
	}

	public String getAllSpotTradesForAllAccounts(String symbol, int limit) {
		try {
			ArrayNode allTrades = mapper.createArrayNode();
			for (AccountBinance account : accountRepo.findByTipo("BINANCE")) {
				if (account.getApiKey() == null || account.getApiSecret() == null) {
					log.debug("Saltando cuenta {} por falta de credenciales.", account.getName());
					continue;
				}
				String response = getSpotTrades(account.getName(), symbol, limit);
				JsonNode parsed = mapper.readTree(response);
				if (parsed.isArray()) {
					for (JsonNode trade : parsed) {
						((ObjectNode) trade).put("account", account.getName());
						allTrades.add(trade);
					}
				} else {
					log.warn("Error al obtener trades para {} con {}: {}", account.getName(), symbol, response);
				}
			}
			return mapper.writeValueAsString(allTrades);
		} catch (Exception e) {
			log.error("Error en getAllSpotTradesForAllAccounts: {}", e.getMessage(), e);
			return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
		}
	}

	public String getSpotTrades(String account, String symbol, int limit) {
		try {
			String[] creds = getApiCredentials(account);
			if (creds == null) return "{\"error\": \"Cuenta no válida.\"}";
			long ts  = getServerTime();
			String q = "symbol=" + symbol + "&limit=" + limit + "&timestamp=" + ts + "&recvWindow=60000";
			return binanceGet("https://api.binance.com/api/v3/myTrades?" + q + "&signature=" + hmacSha256(creds[1], q), creds[0]);
		} catch (Exception e) {
			return "{\"error\": \"Error interno: " + e.getMessage() + "\"}";
		}
	}

	public String getOrderHistory(String apiKey, String secretKey, String symbol, int limit) throws Exception {
		long ts  = getServerTime();
		String q = "symbol=" + symbol + "&timestamp=" + ts + "&recvWindow=60000&limit=" + limit;
		return binanceGet("https://api.binance.com/api/v3/allOrders?" + q + "&signature=" + hmacSha256(secretKey, q), apiKey);
	}

	public String getMyTradesByOrder(String apiKey, String secretKey, String symbol, long orderId) throws Exception {
		long ts  = getServerTime();
		String q = "symbol=" + symbol + "&orderId=" + orderId + "&timestamp=" + ts + "&recvWindow=60000";
		return binanceGet("https://api.binance.com/api/v3/myTrades?" + q + "&signature=" + hmacSha256(secretKey, q), apiKey);
	}

	// ─────────────────────────────────────────────────────────────
	// Precios
	// ─────────────────────────────────────────────────────────────

	public double getPriceInUsdt(String asset) {
		if ("USDT".equalsIgnoreCase(asset)) return 1.0;
		String symbol = asset + "USDT";
		try {
			String resp = binanceGet("https://api.binance.com/api/v3/ticker/price?symbol=" + symbol, null);
			return mapper.readTree(resp).path("price").asDouble(0.0);
		} catch (Exception e) {
			log.warn("No se pudo obtener precio de {}: {}", symbol, e.getMessage());
			return 0.0;
		}
	}

	public Double getPriceInUSDT(String asset) {
		if (asset == null) return null;
		String a = asset.trim().toUpperCase();
		if ("USDT".equals(a) || "USDC".equals(a)) return 1.0;
		String symbol = a + "USDT";
		try {
			String resp = binanceGet("https://api.binance.com/api/v3/ticker/price?symbol=" + symbol, null);
			return mapper.readTree(resp).path("price").asDouble(0.0);
		} catch (Exception e) {
			log.warn("No se pudo obtener precio de {}: {}", symbol, e.getMessage());
			return null;
		}
	}

	/** Descarga todos los pares USDT de Binance. Resultado cacheado por CacheConfig (binancePrices). */
	@Cacheable(value = "binancePrices")
	public Map<String, Double> fetchAllPriceUsdt() throws Exception {
		String resp    = binanceGet("https://api.binance.com/api/v3/ticker/price", null);
		JsonNode tickers = mapper.readTree(resp);
		Map<String, Double> priceMap = new HashMap<>();
		for (JsonNode o : tickers) {
			String sym = o.path("symbol").asText();
			if (sym.endsWith("USDT")) {
				priceMap.put(sym.substring(0, sym.length() - 4), o.path("price").asDouble());
			}
		}
		priceMap.put("USDT", 1.0);
		return priceMap;
	}

	// ─────────────────────────────────────────────────────────────
	// Funding wallet
	// ─────────────────────────────────────────────────────────────

	public double getFundingAssetBalance(String account, String asset) {
		try {
			String[] creds = getApiCredentials(account);
			if (creds == null) throw new RuntimeException("Cuenta no válida");
			long ts     = getServerTime();
			String body = "asset=" + asset + "&timestamp=" + ts + "&recvWindow=60000&signature=" + hmacSha256(creds[1], "asset=" + asset + "&timestamp=" + ts + "&recvWindow=60000");
			String resp = binancePost("https://api.binance.com/sapi/v1/asset/get-funding-asset", creds[0], body);

			for (JsonNode o : mapper.readTree(resp)) {
				if (asset.equalsIgnoreCase(o.path("asset").asText())) {
					return o.path("free").asDouble(0.0) + o.path("freeze").asDouble(0.0);
				}
			}
			return 0.0;
		} catch (Exception e) {
			throw new RuntimeException("Error al obtener Funding balance de " + asset + ": " + e.getMessage(), e);
		}
	}

	public Map<String, Double> getFundingBalancesMap(String account) throws Exception {
		String[] creds = getApiCredentials(account);
		if (creds == null) throw new RuntimeException("Cuenta no válida");
		long ts     = getServerTime();
		String params = "timestamp=" + ts + "&recvWindow=60000";
		String body   = params + "&signature=" + hmacSha256(creds[1], params);
		String resp   = binancePost("https://api.binance.com/sapi/v1/asset/get-funding-asset", creds[0], body);

		Map<String, Double> map = new HashMap<>();
		for (JsonNode o : mapper.readTree(resp)) {
			double qty = o.path("free").asDouble(0.0) + o.path("freeze").asDouble(0.0);
			if (qty > 0) map.merge(o.path("asset").asText(), qty, Double::sum);
		}
		return map;
	}

	// ─────────────────────────────────────────────────────────────
	// Balance general
	// ─────────────────────────────────────────────────────────────

	public Map<String, Double> getAllBalancesByAsset(String account) {
		try {
			Map<String, Double> spot = getSpotBalancesMap(account);
			getFundingBalancesMap(account).forEach((k, v) -> spot.merge(k, v, Double::sum));
			return spot;
		} catch (Exception e) {
			throw new RuntimeException("Error obteniendo balances de " + account + ": " + e.getMessage(), e);
		}
	}

	public Double getGeneralBalanceInUSDT(String account) {
		try {
			String[] creds = getApiCredentials(account);
			if (creds == null) throw new RuntimeException("Cuenta no válida");

			Map<String, Double> priceMap = fetchAllPriceUsdt();
			double total = 0.0;
			long ts = getServerTime();
			String q = "timestamp=" + ts + "&recvWindow=60000";

			// Spot
			try {
				String spotRaw = binanceGet("https://api.binance.com/api/v3/account?" + q + "&signature=" + hmacSha256(creds[1], q), creds[0]);
				for (JsonNode b : mapper.readTree(spotRaw).path("balances")) {
					double qty = b.path("free").asDouble() + b.path("locked").asDouble();
					if (qty <= 0) continue;
					Double price = priceMap.get(b.path("asset").asText());
					if (price != null) total += qty * price;
				}
			} catch (Exception ex) { log.warn("Error Spot balance: {}", ex.getMessage()); }

			// Futures
			try {
				String futUrl = "https://fapi.binance.com/fapi/v2/balance?" + q + "&signature=" + hmacSha256(creds[1], q);
				String futRaw = binanceGet(futUrl, creds[0]);
				JsonNode futNode = mapper.readTree(futRaw);
				if (futNode.isArray()) {
					for (JsonNode b : futNode) {
						Double price = priceMap.get(b.path("asset").asText());
						if (price != null) total += b.path("balance").asDouble() * price;
					}
				}
			} catch (Exception ex) { log.warn("Error Futures balance: {}", ex.getMessage()); }

			// Funding
			try { total += getFundingAssetBalance(account, "USDT"); }
			catch (Exception ex) { log.warn("Error Funding balance: {}", ex.getMessage()); }

			return total;
		} catch (Exception e) {
			throw new RuntimeException("Error general en cálculo de balance USDT: " + e.getMessage(), e);
		}
	}

	// ─────────────────────────────────────────────────────────────
	// Exchange info / assets
	// ─────────────────────────────────────────────────────────────

	public Set<String> getNonZeroAssets(String account) {
		try {
			Set<String> out = new HashSet<>();
			getSpotBalancesMap(account).forEach((asset, qty) -> { if (qty > 0) out.add(asset); });
			return out;
		} catch (Exception e) {
			throw new RuntimeException("Error al leer balances de " + account + ": " + e.getMessage(), e);
		}
	}

	public Set<String> getTradableSymbolsByQuotes(Collection<String> quotes) {
		try {
			String resp = binanceGet("https://api.binance.com/api/v3/exchangeInfo", null);
			JsonNode syms = mapper.readTree(resp).path("symbols");
			Set<String> out = new HashSet<>();
			for (JsonNode s : syms) {
				if ("TRADING".equalsIgnoreCase(s.path("status").asText())
						&& quotes.contains(s.path("quoteAsset").asText())) {
					out.add(s.path("symbol").asText());
				}
			}
			return out;
		} catch (Exception e) {
			throw new RuntimeException("No pude cargar exchangeInfo: " + e.getMessage(), e);
		}
	}

	// ─────────────────────────────────────────────────────────────
	// Anuncios P2P (market data público)
	// ─────────────────────────────────────────────────────────────

	private List<AnuncioDto> obtenerAnunciosPorTipo(Map<String, Object> filtros, String tipo, String cuenta) {
		String url = "https://p2p.binance.com/bapi/c2c/v2/friendly/c2c/adv/search";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Accept-Encoding", "identity");

		Map<String, Object> body = new HashMap<>();
		body.put("asset",         filtros.getOrDefault("asset", "USDT"));
		body.put("fiat",          filtros.getOrDefault("fiat", "COP"));
		body.put("tradeType",     tipo);
		body.put("payTypes",      filtros.getOrDefault("payTypes", List.of()));
		body.put("page",          filtros.getOrDefault("page", 1));
		body.put("rows",          filtros.getOrDefault("rows", 10));
		body.put("publisherType", filtros.getOrDefault("publisherType", null));

		ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);

		List<AnuncioDto> result = new ArrayList<>();
		try {
			JsonNode root = new ObjectMapper().readTree(response.getBody());
			for (JsonNode item : root.path("data")) {
				JsonNode adv        = item.path("adv");
				JsonNode advertiser = item.path("advertiser");

				AnuncioDto anuncio = new AnuncioDto();
				anuncio.setCuenta(cuenta);
				anuncio.setPrecio(adv.path("price").asText(""));
				anuncio.setMoneda(adv.path("asset").asText(""));
				anuncio.setFiat(adv.path("fiatUnit").asText(""));
				anuncio.setMinimo(adv.path("minSingleTransAmount").asText(""));
				anuncio.setMaximo(adv.path("maxSingleTransAmount").asText(""));
				JsonNode methods = adv.path("tradeMethods");
				anuncio.setMetodoPago(methods.isArray() && methods.size() > 0
						? methods.get(0).path("tradeMethodName").asText("") : "");
				anuncio.setVendedor(advertiser.path("nickName").asText(""));
				anuncio.setTipo(adv.path("tradeType").asText(""));

				long ms = adv.path("createTime").asLong(0);
				ZonedDateTime dt = Instant.ofEpochMilli(ms).atZone(ZONE_BOGOTA);
				anuncio.setHoraAnuncio(dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

				result.add(anuncio);
			}
		} catch (Exception e) {
			log.error("Error parseando anuncios P2P ({}): {}", tipo, e.getMessage());
		}
		return result;
	}

	public List<AnuncioDto> obtenerTodosLosAnuncios(Map<String, Object> filtros) {
		List<AnuncioDto> anuncios = new ArrayList<>();
		for (String cuenta : getAllAccountNames()) {
			anuncios.addAll(obtenerAnunciosPorTipo(filtros, "BUY", cuenta));
			anuncios.addAll(obtenerAnunciosPorTipo(filtros, "SELL", cuenta));
		}
		return anuncios;
	}
}
