package com.binance.web.BinanceAPI;

import com.binance.web.model.BuyDollarsDto;
import com.binance.web.model.SellDollarsDto;
import com.binance.web.Entity.Cliente;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SolscanService {

	private final RestTemplate restTemplate = new RestTemplate();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${solscan.apiKey:}")
	private String solscanApiKey;

	// Fallback alternativo (muy estable)
	@Value("${helius.apiKey:}")
	private String heliusApiKey;

	private final BinanceService binanceService;

	public SolscanService(BinanceService binanceService) {
		this.binanceService = binanceService;
	}

	/* ===================== Bases ===================== */
	// PRO con v2 incluido en la base (más simple para tus métodos):
	private static final String PRO_BASE_V2 = "https://pro-api.solscan.io/v2";
	private static final String PRO_BASE = "https://pro-api.solscan.io";
	private static final String PUB_BASE = "https://api.solscan.io";
	private static final String HELIUS_BASE = "https://api.helius.xyz";

	/* ===================== Headers ===================== */
	private HttpHeaders commonHeaders() {
		HttpHeaders h = new HttpHeaders();
		h.set("accept", "application/json");
		h.set("accept-language", "es-ES,es;q=0.9,en;q=0.8");
		h.set("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
				+ "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36");
		h.set("origin", "https://solscan.io");
		h.set("referer", "https://solscan.io/");
		if (solscanApiKey != null && !solscanApiKey.isBlank()) {
			// distintos despliegues aceptan alguno de estos
			h.set("token", solscanApiKey.trim());
			h.set("Authorization", "Bearer " + solscanApiKey.trim());
			h.set("X-API-KEY", solscanApiKey.trim());
		}
		return h;
	}

	// ====== Helper genérico para probar varias URLs en orden ======
	private String tryUrlsInOrder(List<String> urls) {
		for (String url : urls) {
			try {
				return restTemplate.exchange(url, HttpMethod.GET, solscanEntity(), String.class).getBody();
			} catch (HttpClientErrorException.Forbidden e) {
				// 403: prueba la siguiente URL
				continue;
			} catch (HttpClientErrorException.NotFound e) {
				// 404: prueba la siguiente URL
				continue;
			} catch (Exception e) {
				// otros errores (429, 5xx, timeouts...): intenta siguiente
				continue;
			}
		}
		return null; // todas fallaron
	}

	private HttpEntity<Void> solscanEntity() {
		return new HttpEntity<>(commonHeaders());
	}

	private String pickBase() {
		return (solscanApiKey != null && !solscanApiKey.isBlank()) ? PRO_BASE : PUB_BASE;
	}

	// ====== account (balance SOL) con el mismo patrón ======
	public String getAccountRaw(String address) {
		String v2 = PRO_BASE_V2 + "/account?address=" + address;
		String pro = PRO_BASE + "/account?address=" + address;
		String pub = PUB_BASE + "/account?address=" + address;

		String body = tryUrlsInOrder(List.of(v2, pro, pub));
		if (body != null)
			return body;
		return "{\"lamports\":0}"; // respuesta mínima segura
	}

	// ====== account/tokens con el mismo patrón ======
	public String getAccountTokensRaw(String address) {
		String q = "?address=" + address + "&price=1";
		String v2 = PRO_BASE_V2 + "/account/tokens" + q;
		String pro = PRO_BASE + "/account/tokens" + q;
		String pub = PUB_BASE + "/account/tokens" + q;

		String body = tryUrlsInOrder(List.of(v2, pro, pub));
		if (body != null)
			return body;
		return "{\"data\":[]}";
	}

	// ====== solTransfers (movimientos de SOL) ======
	public String getSolTransfersRaw(String address, int limit) {
		String q = "?address=" + address + "&limit=" + (limit <= 0 ? 50 : limit);
		String v2 = PRO_BASE_V2 + "/account/solTransfers" + q;
		String pro = PRO_BASE + "/account/solTransfers" + q;
		String pub = PUB_BASE + "/account/solTransfers" + q;

		String body = tryUrlsInOrder(List.of(v2, pro, pub));
		if (body != null)
			return body;

		// no hay fallback a Helius aquí (puedes añadir si te interesa)
		return "{\"data\":[]}";
	}

	// ====== transaction (para leer fee) con el mismo patrón y fallback Helius
	// ======
	public JsonNode getTxDetails(String signature) {
		String q = "?tx=" + signature;
		String v2 = PRO_BASE_V2 + "/transaction" + q;
		String pro = PRO_BASE + "/transaction" + q;
		String pub = PUB_BASE + "/transaction" + q;

		String body = tryUrlsInOrder(List.of(v2, pro, pub));
		if (body != null) {
			try {
				return objectMapper.readTree(body);
			} catch (Exception ignore) {
			}
		}
		// Fallback a Helius solo para conseguir el fee
		if (heliusApiKey == null || heliusApiKey.isBlank())
			return objectMapper.createObjectNode();
		try {
			HttpHeaders h = new HttpHeaders();
			h.setContentType(MediaType.APPLICATION_JSON);
			h.setAccept(List.of(MediaType.APPLICATION_JSON));
			String txUrl = HELIUS_BASE + "/v0/transactions?api-key=" + heliusApiKey.trim();
			ArrayNode bodyArr = objectMapper.createArrayNode();
			bodyArr.add(signature);
			HttpEntity<String> req = new HttpEntity<>(bodyArr.toString(), h);
			String raw = restTemplate.exchange(txUrl, HttpMethod.POST, req, String.class).getBody();
			ArrayNode arr = (ArrayNode) objectMapper.readTree(raw);
			if (arr.size() == 0)
				return objectMapper.createObjectNode();

			long fee = arr.get(0).path("fee").asLong(0L);
			ObjectNode out = objectMapper.createObjectNode();
			ObjectNode meta = objectMapper.createObjectNode();
			meta.put("fee", fee);
			out.set("meta", meta);
			return out;
		} catch (Exception ignore) {
			return objectMapper.createObjectNode();
		}
	}

	/*
	 * ===================== Fallback Helius → formateo estilo Solscan
	 * =====================
	 */

	private static final Set<String> SOLANA_WHITELIST = Set.of("SOL", "USDC", "USDT");

	// Cambia esta línea en tu SolscanService (línea ~170 aprox)
	private static final Map<String, String> MINT_TO_SYMBOL = Map.of(
	    "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", "USDC",  // ✅ Correcto
	    "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", "USDT"
	);

	private static final Map<String, Integer> SYMBOL_DECIMALS = Map.of("USDC", 6, "USDT", 6);

	/**
	 * Devuelve un JSON con campo "data":[...] compatible con los parsers actuales.
	 */
	private String heliusTransfersAsSolscan(String address, int limit) {
		try {
			String url = HELIUS_BASE + "/v0/addresses/" + address + "/transactions?api-key=" + heliusApiKey.trim()
					+ "&limit=" + limit;
			System.out.println("➡️ Helius TX URL=" + url);

			HttpHeaders h = new HttpHeaders();
			h.setAccept(List.of(MediaType.APPLICATION_JSON));
			String raw = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class).getBody();

			ArrayNode hel = (ArrayNode) objectMapper.readTree(raw);
			ArrayNode data = objectMapper.createArrayNode();

			for (JsonNode tx : hel) {
				String sig = tx.path("signature").asText(null);
				long ts = tx.path("timestamp").asLong(0L);

				// tokenTransfers → generamos una “fila” por cada transferencia SPL
				JsonNode tokenTransfers = tx.path("tokenTransfers");
				if (tokenTransfers.isArray()) {
					for (JsonNode t : tokenTransfers) {
						String from = t.path("fromUserAccount").asText(null);
						String to = t.path("toUserAccount").asText(null);
						String mint = t.path("mint").asText(null);

						String symbol = MINT_TO_SYMBOL.getOrDefault(mint, null);
						String uiAmount = t.path("tokenAmount").asText(null);
						if (symbol == null || uiAmount == null || sig == null)
							continue;

						ObjectNode row = objectMapper.createObjectNode();
						row.put("src", from);
						row.put("dst", to);
						row.put("signature", sig);
						row.put("blockTime", ts);
						row.put("symbol", symbol);

						int dec = SYMBOL_DECIMALS.getOrDefault(symbol, 6);
						// nuestro parser acepta uiAmountString, así evitamos cruce decimales
						ObjectNode tokenAmount = objectMapper.createObjectNode();
						tokenAmount.put("uiAmountString", uiAmount);
						tokenAmount.put("decimals", dec);
						row.set("tokenAmount", tokenAmount);
						data.add(row);
					}
				}
			}
			ObjectNode out = objectMapper.createObjectNode();
			out.set("data", data);
			return out.toString();
		} catch (Exception e) {
			System.out.println("⚠️ Error Helius fallback: " + e.getMessage());
			return "{\"data\":[]}";
		}
	}

	// Reemplaza getBalancesByAsset para que use Helius directamente
	public Map<String, Double> getBalancesByAsset(String address) {
	    // IR DIRECTO A HELIUS
	    if (heliusApiKey != null && !heliusApiKey.isBlank()) {
	        return getBalancesByAssetHeliusOnly(address);
	    }
	    
	    // Fallback vacío si no hay Helius
	    return new HashMap<>();
	}

	// Reemplaza getTotalAssetUsd para que use Helius directamente
	public double getTotalAssetUsd(String address) {
	    // IR DIRECTO A HELIUS
	    if (heliusApiKey != null && !heliusApiKey.isBlank()) {
	        return getTotalAssetUsdHeliusOnly(address);
	    }
	    
	    return 0.0;
	}

	/* ===================== Parsers estilo Tron ===================== */

	public List<BuyDollarsDto> parseSplIncomingTransfers(String jsonResponse, String walletAddress, String accountName,
			Set<String> assignedIds) {
		List<BuyDollarsDto> out = new ArrayList<>();
		LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
		try {
			JsonNode root = objectMapper.readTree(jsonResponse);
			JsonNode data = root.isArray() ? root : root.path("data");
			if (!data.isArray())
				return out;

			for (JsonNode tx : data) {
				String to = pickLower(tx.path("dst").asText(null), tx.path("to").asText(null),
						tx.path("destination").asText(null));
				String sig = firstNonBlank(tx.path("txHash").asText(null), tx.path("signature").asText(null),
						tx.path("transactionHash").asText(null));
				String symbol = pickUpper(tx.path("symbol").asText(null), tx.path("tokenSymbol").asText(null));

				String rawValue = firstNonBlank(tx.path("changeAmount").asText(null), tx.path("amount").asText(null),
						tx.path("tokenAmount").path("amount").asText(null));
				int dec = firstNonNeg(tx.path("decimals").asInt(-1), tx.path("tokenAmount").path("decimals").asInt(-1));
				long ts = firstNonZero(tx.path("blockTime").asLong(0L), tx.path("timeStamp").asLong(0L)) * 1000L;

				if (to == null || sig == null || symbol == null)
					continue;
				if (!to.equalsIgnoreCase(walletAddress))
					continue;
				if (assignedIds.contains(sig))
					continue;

				LocalDate fecha = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota"))
						.toLocalDate();
				if (!fecha.isEqual(hoy))
					continue;

				double qty = 0.0;
				try {
					if (rawValue != null && dec >= 0) {
						qty = Double.parseDouble(rawValue) / Math.pow(10, dec);
					} else {
						String ui = firstNonBlank(tx.path("uiAmountString").asText(null),
								tx.path("tokenAmount").path("uiAmountString").asText(null));
						if (ui != null)
							qty = Double.parseDouble(ui);
					}
				} catch (Exception ignore) {
				}

				if (qty <= 0)
					continue;

				BuyDollarsDto dto = new BuyDollarsDto();
				dto.setIdDeposit(sig);
				dto.setNameAccount(accountName);
				dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota")));
				dto.setAmount(qty);
				dto.setCryptoSymbol(symbol);
				dto.setTasa(0.0);
				dto.setPesos(0.0);
				dto.setAsignada(false);
				out.add(dto);
			}
			return out;
		} catch (Exception e) {
			return out;
		}
	}

	public List<SellDollarsDto> parseSplOutgoingTransfers(String jsonResponse, String walletAddress, String accountName,
			Set<String> assignedIds, Map<String, Cliente> clientePorWallet) {
		List<SellDollarsDto> out = new ArrayList<>();
		LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
		try {
			JsonNode root = objectMapper.readTree(jsonResponse);
			JsonNode data = root.isArray() ? root : root.path("data");
			if (!data.isArray())
				return out;

			for (JsonNode tx : data) {
				String from = pickLower(tx.path("src").asText(null), tx.path("from").asText(null),
						tx.path("source").asText(null));
				String to = pickLower(tx.path("dst").asText(null), tx.path("to").asText(null),
						tx.path("destination").asText(null));
				String sig = firstNonBlank(tx.path("txHash").asText(null), tx.path("signature").asText(null),
						tx.path("transactionHash").asText(null));
				String symbol = pickUpper(tx.path("symbol").asText(null), tx.path("tokenSymbol").asText(null));
				String rawValue = firstNonBlank(tx.path("changeAmount").asText(null), tx.path("amount").asText(null),
						tx.path("tokenAmount").path("amount").asText(null));
				int dec = firstNonNeg(tx.path("decimals").asInt(-1), tx.path("tokenAmount").path("decimals").asInt(-1));
				long ts = firstNonZero(tx.path("blockTime").asLong(0L), tx.path("timeStamp").asLong(0L)) * 1000L;

				if (from == null || sig == null || symbol == null)
					continue;
				if (!from.equalsIgnoreCase(walletAddress))
					continue;
				if (assignedIds.contains(sig))
					continue;

				LocalDate fecha = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota"))
						.toLocalDate();
				if (!fecha.isEqual(hoy))
					continue;

				double qty = 0.0;
				try {
					if (rawValue != null && dec >= 0) {
						qty = Double.parseDouble(rawValue) / Math.pow(10, dec);
					} else {
						String ui = firstNonBlank(tx.path("uiAmountString").asText(null),
								tx.path("tokenAmount").path("uiAmountString").asText(null));
						if (ui != null)
							qty = Double.parseDouble(ui);
					}
				} catch (Exception ignore) {
				}

				if (qty <= 0)
					continue;

				double feeSol = 0.0;
				try {
					JsonNode txDetails = getTxDetails(sig);
					long feeLamports = txDetails.path("meta").path("fee").asLong(txDetails.path("fee").asLong(0L));
					feeSol = feeLamports / 1_000_000_000.0;
				} catch (Exception ignore) {
				}

				SellDollarsDto dto = new SellDollarsDto();
				dto.setIdWithdrawals(sig);
				dto.setNameAccount(accountName);
				dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota")));
				dto.setDollars(qty);
				dto.setCryptoSymbol(symbol);
				dto.setNetworkFeeInSOL(feeSol);
				dto.setComision(0.0); // muy importante para no restar fee del token
				dto.setTasa(0.0);
				dto.setPesos(0.0);

				if (to != null && clientePorWallet != null) {
					Cliente c = clientePorWallet.get(to.trim().toLowerCase());
					if (c != null)
						dto.setClienteId(c.getId());
				}
				out.add(dto);
			}
			return out;
		} catch (Exception e) {
			return out;
		}
	}

	/* ===================== Utils ===================== */
	private static String pickUpper(String... vals) {
		for (String v : vals)
			if (v != null && !v.isBlank())
				return v.trim().toUpperCase();
		return null;
	}

	private static String pickLower(String... vals) {
		for (String v : vals)
			if (v != null && !v.isBlank())
				return v.trim().toLowerCase();
		return null;
	}

	private static String firstNonBlank(String... vals) {
		for (String v : vals)
			if (v != null && !v.isBlank())
				return v;
		return null;
	}

	private static int firstNonNeg(int... vals) {
		for (int v : vals)
			if (v >= 0)
				return v;
		return -1;
	}

	private static long firstNonZero(long... vals) {
		for (long v : vals)
			if (v != 0L)
				return v;
		return 0L;
	}

	// REEMPLAZA COMPLETAMENTE el método getSplTransfersRaw existente
	public String getSplTransfersRaw(String address, int limit) {
	    int lim = (limit <= 0 ? 50 : limit);

	    // 🔥 IR DIRECTO A HELIUS - SIN INTENTAR SOLSCAN
	    if (heliusApiKey != null && !heliusApiKey.isBlank()) {
	        System.out.println("🚀 Usando Helius directamente (Solscan no disponible)");
	        return heliusTransfersAsSolscan(address, lim);
	    }
	    
	    System.out.println("⚠️ No hay API key de Helius configurada");
	    return "{\"data\":[]}";
	}

	/* ===================== Wrappers de alto nivel ===================== */

	public List<BuyDollarsDto> listIncomingToday(String walletAddress, String accountName, Set<String> assignedIds) {
	    // CAMBIA getSplTransfersRaw por getSplTransfersRawHeliusOnly
	    String raw = getSplTransfersRawHeliusOnly(walletAddress, 20);
	    return parseSplIncomingTransfers(raw, walletAddress, accountName, assignedIds);
	}

	public List<SellDollarsDto> listOutgoingToday(String walletAddress, String accountName, Set<String> assignedIds,
	        Map<String, Cliente> clientePorWallet) {
	    // CAMBIA getSplTransfersRaw por getSplTransfersRawHeliusOnly
	    String raw = getSplTransfersRawHeliusOnly(walletAddress, 20);
	    List<SellDollarsDto> out = parseSplOutgoingTransfers(raw, walletAddress, accountName, assignedIds,
	            clientePorWallet);
	    out.forEach(dto -> dto.setComision(0.0));
	    return out;
	}

	public List<BuyDollarsDto> parseSplIncomingTransfersHistory(String jsonResponse, String walletAddress,
			String accountName, Set<String> assignedIds) {
		List<BuyDollarsDto> out = new ArrayList<>();
		try {
			JsonNode root = objectMapper.readTree(jsonResponse);
			JsonNode data = root.isArray() ? root : root.path("data");
			if (!data.isArray())
				return out;

			for (JsonNode tx : data) {
				String to = pickLower(tx.path("dst").asText(null), tx.path("to").asText(null),
						tx.path("destination").asText(null));
				String sig = firstNonBlank(tx.path("txHash").asText(null), tx.path("signature").asText(null),
						tx.path("transactionHash").asText(null));
				String symbol = pickUpper(tx.path("symbol").asText(null), tx.path("tokenSymbol").asText(null));
				if (symbol == null) {
					// fallback desde mint si viene sin símbolo
					String mint = firstNonBlank(tx.path("mint").asText(null), tx.path("tokenAddress").asText(null),
							tx.path("tokenMint").asText(null));
					if (mint != null)
						symbol = MINT_TO_SYMBOL.get(mint);
				}

				String rawValue = firstNonBlank(tx.path("changeAmount").asText(null), tx.path("amount").asText(null),
						tx.path("tokenAmount").path("amount").asText(null));
				int dec = firstNonNeg(tx.path("decimals").asInt(-1), tx.path("tokenAmount").path("decimals").asInt(-1));
				long ts = firstNonZero(tx.path("blockTime").asLong(0L), tx.path("timeStamp").asLong(0L)) * 1000L;

				if (to == null || sig == null || symbol == null)
					continue;
				if (!to.equalsIgnoreCase(walletAddress))
					continue;
				if (assignedIds.contains(sig))
					continue;

				double qty = 0.0;
				try {
					if (rawValue != null && dec >= 0) {
						qty = Double.parseDouble(rawValue) / Math.pow(10, dec);
					} else {
						String ui = firstNonBlank(tx.path("uiAmountString").asText(null),
								tx.path("tokenAmount").path("uiAmountString").asText(null));
						if (ui != null)
							qty = Double.parseDouble(ui);
					}
				} catch (Exception ignore) {
				}

				if (qty <= 0)
					continue;

				BuyDollarsDto dto = new BuyDollarsDto();
				dto.setIdDeposit(sig);
				dto.setNameAccount(accountName);
				dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota")));
				dto.setAmount(qty);
				dto.setCryptoSymbol(symbol);
				dto.setTasa(0.0);
				dto.setPesos(0.0);
				dto.setAsignada(false);
				out.add(dto);
			}
			return out;
		} catch (Exception e) {
			return out;
		}
	}

	public List<BuyDollarsDto> listIncomingHistory(String walletAddress, String accountName, Set<String> assignedIds,
			int limit) {
		String raw = getSplTransfersRaw(walletAddress, limit <= 0 ? 20 : limit);
		return parseSplIncomingTransfersHistory(raw, walletAddress, accountName, assignedIds);
	}

	// Solo Solscan (sin fallback)
	public String getSplTransfersRawOnlySolscan(String address, int limit) {
		int lim = (limit <= 0 ? 20 : limit);
		List<String> urls = List.of(
				// PRO v2
				PRO_BASE_V2 + "/account/splTransfers?address=" + address + "&limit=" + lim,
				PRO_BASE_V2 + "/account/splTransfers?account=" + address + "&limit=" + lim,
				// PRO raíz
				PRO_BASE + "/v2/account/splTransfers?address=" + address + "&limit=" + lim,
				PRO_BASE + "/v2/account/splTransfers?account=" + address + "&limit=" + lim,
				PRO_BASE + "/account/splTransfers?address=" + address + "&limit=" + lim,
				PRO_BASE + "/account/splTransfers?account=" + address + "&limit=" + lim,
				// Público
				PUB_BASE + "/v2/account/splTransfers?address=" + address + "&limit=" + lim,
				PUB_BASE + "/v2/account/splTransfers?account=" + address + "&limit=" + lim,
				PUB_BASE + "/account/splTransfers?address=" + address + "&limit=" + lim,
				PUB_BASE + "/account/splTransfers?account=" + address + "&limit=" + lim);
		String body = tryUrlsInOrder(urls);
		return (body != null) ? body : "{\"data\":[]}";
	}

	// Helper para saber si el body trae data usable
	private boolean hasUsableData(String body) {
		try {
			JsonNode root = objectMapper.readTree(body);
			JsonNode data = root.isArray() ? root : root.path("data");
			return data != null && data.isArray() && data.size() > 0;
		} catch (Exception e) {
			return false;
		}
	}

	// headers SOLO con Bearer (no mezclar X-API-KEY/token/etc.)
	private HttpEntity<Void> solscanProEntity() {
		HttpHeaders h = new HttpHeaders();
		h.setAccept(List.of(MediaType.APPLICATION_JSON));
		if (solscanApiKey != null && !solscanApiKey.isBlank()) {
			h.setBearerAuth(solscanApiKey.trim());
		}
		return new HttpEntity<>(h);
	}

	// Ajusta el page_size a los permitidos por Solscan: 10,20,30,40,60,100
	private int normalizePageSize(int limit) {
		int n = (limit <= 0 ? 20 : limit);
		if (n <= 10)
			return 10;
		if (n <= 20)
			return 20;
		if (n <= 30)
			return 30;
		if (n <= 40)
			return 40;
		if (n <= 60)
			return 60;
		return 100;
	}

	// 🔹 ÚNICO método: historial crudo desde Solscan Pro v2
	public String getTransfersHistoryRaw(String address, int limit) {
		if (address == null || address.isBlank())
			return "{\"success\":false,\"data\":[],\"error\":\"missing address\"}";
		int pageSize = normalizePageSize(limit);

		String url = PRO_BASE + "/v2.0/account/transfer" + "?address="
				+ UriUtils.encode(address.trim(), StandardCharsets.UTF_8) + "&page=1" + "&page_size=" + pageSize
				+ "&sort_by=block_time&sort_order=desc" + "&exclude_amount_zero=true";

		try {
			ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, solscanProEntity(), String.class);
			String body = res.getBody();
			return (body != null && !body.isBlank()) ? body : "{\"success\":false,\"data\":[]}";
		} catch (HttpStatusCodeException e) {
			// verás el código exacto en logs si algo falla
			System.out.println("Solscan error " + e.getStatusCode() + " -> " + e.getResponseBodyAsString());
			return "{\"success\":false,\"data\":[]}";
		} catch (Exception e) {
			System.out.println("Solscan error -> " + e.getMessage());
			return "{\"success\":false,\"data\":[]}";
		}
	}

	private HttpHeaders headers() {
		HttpHeaders h = new HttpHeaders();
		h.setAccept(List.of(MediaType.APPLICATION_JSON));
		// Solscan Pro API usa el header "token", NO Bearer
		if (solscanApiKey == null || solscanApiKey.isBlank()) {
			throw new IllegalStateException("solscan.apiKey no está configurado (perfil activo).");
		}
		h.set("token", solscanApiKey.trim());
		return h;
	}

	/**
	 * Historial crudo desde Solscan Pro: /v2.0/account/transfer page >=1, pageSize
	 * (default 20). tokenType: all|SOL|SPL (opcional).
	 */
	public String getTransfersRaw(String address, Integer page, Integer pageSize, String tokenType) {
		int p = (page == null || page < 1) ? 1 : page;
		int ps = (pageSize == null || pageSize <= 0) ? 20 : pageSize;

		StringBuilder url = new StringBuilder(PRO_BASE_V2).append("/account/transfer?address=")
				.append(URLEncoder.encode(address, StandardCharsets.UTF_8)).append("&page=").append(p)
				.append("&page_size=").append(ps);

		if (tokenType != null && !tokenType.isBlank()) {
			url.append("&tokenType=").append(URLEncoder.encode(tokenType, StandardCharsets.UTF_8));
		}

		try {
			return restTemplate.exchange(url.toString(), HttpMethod.GET, new HttpEntity<>(headers()), String.class)
					.getBody();
		} catch (HttpClientErrorException e) {
			// Devuelve el JSON esperado por tu front aunque haya fallo
			return "{\"success\":false,\"status\":" + e.getStatusCode().value() + ",\"message\":"
					+ objectMapper.valueToTree(e.getResponseBodyAsString()) + ",\"data\":[]}";
		} catch (Exception e) {
			return "{\"success\":false,\"message\":\"" + e.getMessage() + "\",\"data\":[]}";
		}
	}
	
	public Map<String, Object> testTransfersEndpoints(String address) {
	    Map<String, Object> result = new HashMap<>();
	    
	    // Lista de endpoints a probar
	    List<String> endpoints = List.of(
	        PRO_BASE + "/v2.0/account/transfer?address=" + address + "&page=1&page_size=20",
	        PRO_BASE_V2 + "/account/transfer?address=" + address + "&page=1&page_size=20",
	        PRO_BASE + "/v2/account/transfer?address=" + address + "&page=1&page_size=20",
	        PRO_BASE + "/account/transfer?address=" + address + "&page=1&page_size=20",
	        PRO_BASE_V2 + "/account/splTransfers?address=" + address + "&limit=20",
	        PRO_BASE + "/account/splTransfers?address=" + address + "&limit=20",
	        PUB_BASE + "/account/splTransfers?address=" + address + "&limit=20"
	    );
	    
	    List<Map<String, Object>> results = new ArrayList<>();
	    
	    for (String endpoint : endpoints) {
	        Map<String, Object> testResult = new HashMap<>();
	        testResult.put("endpoint", endpoint);
	        
	        try {
	            // Probar con header "token"
	            HttpHeaders h = new HttpHeaders();
	            h.setAccept(List.of(MediaType.APPLICATION_JSON));
	            if (solscanApiKey != null && !solscanApiKey.isBlank()) {
	                h.set("token", solscanApiKey.trim());
	            }
	            
	            ResponseEntity<String> res = restTemplate.exchange(
	                endpoint, HttpMethod.GET, new HttpEntity<>(h), String.class
	            );
	            
	            testResult.put("status", res.getStatusCodeValue());
	            testResult.put("success", true);
	            String body = res.getBody();
	            if (body != null) {
	                testResult.put("body_preview", body.substring(0, Math.min(200, body.length())));
	                testResult.put("has_data", hasUsableData(body));
	            }
	            results.add(testResult);
	            
	        } catch (HttpStatusCodeException e) {
	            testResult.put("status", e.getStatusCode().value());
	            testResult.put("success", false);
	            testResult.put("error", e.getMessage());
	            testResult.put("response_body", e.getResponseBodyAsString());
	            results.add(testResult);
	        } catch (Exception e) {
	            testResult.put("success", false);
	            testResult.put("error", e.getMessage());
	            results.add(testResult);
	        }
	    }
	    
	    result.put("tests", results);
	    result.put("total_tests", results.size());
	    result.put("successful", results.stream().filter(r -> (Boolean) r.getOrDefault("success", false)).count());
	    
	    return result;
	}
	public String getSplTransfersRawHeliusOnly(String address, int limit) {
	    int lim = (limit <= 0 ? 50 : limit);
	    
	    // 🔥 HELIUS SOLO ACEPTA MÁXIMO 100
	    if (lim > 100) {
	        lim = 100;
	    }
	    
	    if (heliusApiKey == null || heliusApiKey.isBlank()) {
	        return "{\"data\":[]}";
	    }
	    
	    System.out.println("🚀 Usando Helius para SPL transfers (limit=" + lim + ")");
	    return heliusTransfersAsSolscan(address, lim);
	}
	
	// En SolscanService
	public boolean isHeliusConfigured() {
	    return heliusApiKey != null && !heliusApiKey.isBlank();
	}

	public boolean isSolscanConfigured() {
	    return solscanApiKey != null && !solscanApiKey.isBlank();
	}
	
	// Nuevo método que usa SOLO Helius para obtener balances
	public Map<String, Double> getBalancesByAssetHeliusOnly(String address) {
	    Map<String, Double> out = new HashMap<>();
	    
	    if (heliusApiKey == null || heliusApiKey.isBlank()) {
	        return out;
	    }
	    
	    try {
	        // Helius tiene endpoint para obtener balances de tokens
	        String url = HELIUS_BASE + "/v0/addresses/" + address + "/balances?api-key=" + heliusApiKey.trim();
	        
	        HttpHeaders h = new HttpHeaders();
	        h.setAccept(List.of(MediaType.APPLICATION_JSON));
	        String raw = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class).getBody();
	        
	        JsonNode response = objectMapper.readTree(raw);
	        
	        // Procesar SOL nativo
	        long lamports = response.path("nativeBalance").asLong(0L);
	        double sol = lamports / 1_000_000_000.0;
	        if (sol > 0) {
	            out.put("SOL", sol);
	        }
	        
	        // Procesar tokens SPL
	        JsonNode tokens = response.path("tokens");
	        if (tokens != null && tokens.isArray()) {
	            for (JsonNode token : tokens) {
	                String mint = token.path("mint").asText(null);
	                String symbol = MINT_TO_SYMBOL.get(mint);
	                
	                if (symbol != null && SOLANA_WHITELIST.contains(symbol)) {
	                    double amount = token.path("amount").asDouble(0.0);
	                    int decimals = token.path("decimals").asInt(6);
	                    double qty = amount / Math.pow(10, decimals);
	                    
	                    if (qty > 0) {
	                        out.put(symbol, qty);
	                    }
	                }
	            }
	        }
	        
	        return out;
	    } catch (Exception e) {
	        System.out.println("⚠️ Error obteniendo balances de Helius: " + e.getMessage());
	        return out;
	    }
	}

	// Versión simplificada que calcula total en USD usando Helius
	public double getTotalAssetUsdHeliusOnly(String address) {
	    Map<String, Double> balances = getBalancesByAssetHeliusOnly(address);
	    double total = 0.0;
	    
	    for (Map.Entry<String, Double> e : balances.entrySet()) {
	        String sym = e.getKey();
	        double qty = e.getValue();
	        double px = ("USDT".equalsIgnoreCase(sym) || "USDC".equalsIgnoreCase(sym)) ? 1.0
	                : Optional.ofNullable(binanceService.getPriceInUSDT(sym)).orElse(0.0);
	        total += qty * px;
	    }
	    
	    return total;
	}
}
