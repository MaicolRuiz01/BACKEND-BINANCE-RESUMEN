package com.binance.web.BinanceAPI;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.SpotOrder;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.SpotOrderRepository;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

//SpotOrderIngestService.java
@Service
@RequiredArgsConstructor
@Transactional
public class SpotOrderIngestService {

	private final BinanceService binanceService;
	private final AccountBinanceRepository accountRepo;
	private final SpotOrderRepository spotOrderRepo;
	private final AccountBinanceService accountService;

	private static final List<String> QUOTES = List.of("USDT", "USDC", "FDUSD", "BUSD", "TUSD", "USDP", "DAI");
	private static final Set<String> SIMBOLOS_BASE_FIJOS = Set.of("BTC", "ETH", "TRX", "BNB", "XRP", "SOL", "TON"); // opcional

	private String detectQuote(String s) {
		String u = s.toUpperCase();
		for (String q : QUOTES)
			if (u.endsWith(q))
				return q;
		throw new RuntimeException("Símbolo no soportado (quote): " + s);
	}

	/** Deducción dinámica de símbolos a consultar para UNA cuenta. */
	private Set<String> resolverSimbolosParaCuenta(String accountName) {
		Set<String> assets = new HashSet<>(binanceService.getNonZeroAssets(accountName));
		assets.addAll(SIMBOLOS_BASE_FIJOS); // cobertura adicional
		Set<String> symbols = new HashSet<>();
		for (String base : assets) {
			String b = base.trim().toUpperCase();
			if (b.isBlank())
				continue;
			for (String q : QUOTES) {
				// Evita “USDTUSDT”
				if (b.equalsIgnoreCase(q))
					continue;
				symbols.add(b + q);
			}
		}
		return symbols;
	}

	/**
	 * Importa TODO para todas las cuentas BINANCE, símbolos deducidos, límite por
	 * símbolo.
	 */
	public int importarTodasLasCuentasAuto(int limitPorSimbolo) {
	    Set<String> tradables = binanceService.getTradableSymbolsByQuotes(QUOTES);
	    int total = 0;

	    for (AccountBinance acc : accountRepo.findByTipo("BINANCE")) {
	        Set<String> symbols;
	        try {
	            symbols = resolverSimbolosParaCuenta(acc.getName(), tradables);
	        } catch (RuntimeException e) {
	            System.err.println("⏭️ Saltando cuenta " + acc.getName() + ": " + e.getMessage());
	            continue; // no frenamos todo
	        }

	        for (String s : symbols) {
	            try {
	                total += importSymbol(acc, s, limitPorSimbolo);
	            } catch (Exception ex) {
	                System.err.println("⏭️ Saltando símbolo " + s + " (" + acc.getName() + "): " + ex.getMessage());
	            }
	        }
	    }
	    return total;
	}


	/** Importa para una cuenta con lista de símbolos explícita. */
	public int importarCuenta(String accountName, List<String> symbols, int limit) {
		AccountBinance acc = accountRepo.findByName(accountName);
		if (acc == null || !"BINANCE".equalsIgnoreCase(acc.getTipo()))
			throw new RuntimeException("Cuenta BINANCE no encontrada: " + accountName);

		int inserted = 0;
		for (String s : symbols)
			inserted += importSymbol(acc, s.toUpperCase(), limit);
		return inserted;
	}

	/**
	 * Importa un símbolo: guarda FILLED, calcula comisiones/avg, ajusta balances
	 * (idempotente).
	 */
	private int importSymbol(AccountBinance acc, String symbol, int limit) {
		try {
			String raw = binanceService.getOrderHistory(acc.getName(), symbol, limit);
			JsonElement parsed = JsonParser.parseString(raw);
			if (!parsed.isJsonArray())
				return 0;

			String quote = detectQuote(symbol);
			String base = symbol.substring(0, symbol.length() - quote.length());

			int count = 0;
			for (JsonElement el : parsed.getAsJsonArray()) {
				JsonObject o = el.getAsJsonObject();
				if (!"FILLED".equalsIgnoreCase(o.get("status").getAsString()))
					continue;

				long orderId = o.get("orderId").getAsLong();
				if (spotOrderRepo.existsByAccountAndOrderId(acc, orderId))
					continue; // idempotencia

				String side = o.get("side").getAsString();
				double execBase = o.get("executedQty").getAsDouble();
				double execQ = o.get("cummulativeQuoteQty").getAsDouble();

				// --- fills: comisiones exactas y fecha real de ejecución (último fill)
				String fillsRaw = binanceService.getMyTradesByOrder(acc.getName(), symbol, orderId);
				Map<String, Double> feeByAsset = new HashMap<>();
				double notionalQuote = 0.0, qtyBaseSum = 0.0;
				long lastFillTs = 0L;

				JsonElement fillsParsed = JsonParser.parseString(fillsRaw);
				if (fillsParsed.isJsonArray()) {
					for (JsonElement fe : fillsParsed.getAsJsonArray()) {
						JsonObject f = fe.getAsJsonObject();
						double qty = f.get("qty").getAsDouble();
						double quoteQty = f.get("quoteQty").getAsDouble();
						qtyBaseSum += qty;
						notionalQuote += quoteQty;

						String cAsset = f.get("commissionAsset").getAsString();
						double cQty = f.get("commission").getAsDouble();
						feeByAsset.merge(cAsset, cQty, Double::sum);

						if (f.has("time"))
							lastFillTs = Math.max(lastFillTs, f.get("time").getAsLong());
					}
				}

				double avgPrice = (qtyBaseSum > 0 ? notionalQuote / qtyBaseSum
						: (execBase > 0 ? execQ / execBase : 0.0));

				double feeUsdt = 0.0; // aplanado a USDT
				for (var e : feeByAsset.entrySet()) {
					double px = binanceService.getPriceInUsdt(e.getKey());
					if (px <= 0 && "USDT".equalsIgnoreCase(e.getKey()))
						px = 1.0;
					feeUsdt += e.getValue() * Math.max(px, 0.0);
				}

				// fecha real: último fill; si no hubo fills, usa updateTime o time
				long ts = (lastFillTs > 0 ? lastFillTs
						: (o.has("updateTime") ? o.get("updateTime").getAsLong() : o.get("time").getAsLong()));

				SpotOrder so = new SpotOrder();
				so.setAccount(acc);
				so.setOrderId(orderId);
				so.setClientOrderId(o.get("clientOrderId").getAsString());
				so.setSymbol(symbol);
				so.setBaseAsset(base);
				so.setQuoteAsset(quote);
				so.setSide(side);
				so.setType(o.get("type").getAsString());
				so.setStatus("FILLED");
				so.setExecutedBaseQty(execBase);
				so.setExecutedQuoteQty(execQ);
				so.setAvgPrice(avgPrice);
				so.setFeeTotalUsdt(feeUsdt);
				so.setFeeBreakdownJson(new Gson().toJson(feeByAsset));
				so.setFilledAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota")));
				spotOrderRepo.save(so);

				// Ajuste de balances internos exacto (compra/vende + comisiones por activo)
				applyDeltas(acc, base, quote, side, execBase, execQ, feeByAsset);

				count++;
			}
			return count;
		} catch (Exception e) {
			throw new RuntimeException("Import " + symbol + " (" + acc.getName() + "): " + e.getMessage(), e);
		}
	}

	private void applyDeltas(AccountBinance acc, String base, String quote, String side, double execBase,
			double execQuote, Map<String, Double> feeByAsset) {

		double feeBase = feeByAsset.getOrDefault(base, 0.0);
		double feeQuote = feeByAsset.getOrDefault(quote, 0.0);

		if ("BUY".equalsIgnoreCase(side)) {
			accountService.updateOrCreateCryptoBalance(acc.getId(), base, +execBase - feeBase);
			accountService.updateOrCreateCryptoBalance(acc.getId(), quote, -execQuote - feeQuote);
		} else {
			accountService.updateOrCreateCryptoBalance(acc.getId(), base, -execBase - feeBase);
			accountService.updateOrCreateCryptoBalance(acc.getId(), quote, +execQuote - feeQuote);
		}

		for (var e : feeByAsset.entrySet()) {
			String asset = e.getKey();
			if (asset.equalsIgnoreCase(base) || asset.equalsIgnoreCase(quote))
				continue;
			accountService.updateOrCreateCryptoBalance(acc.getId(), asset, -e.getValue());
		}
	}
	// SpotOrderIngestService.java
	private Set<String> resolverSimbolosParaCuenta(String accountName, Set<String> tradables) {
	    Set<String> assets = new HashSet<>();
	    try {
	        assets.addAll(binanceService.getNonZeroAssets(accountName)); // puede lanzar 401
	    } catch (RuntimeException e) {
	        System.err.println("⚠️ No pude leer balances de " + accountName + ": " + e.getMessage());
	        // seguimos con un set vacío; opcional: agregar seeds
	    }

	    // opcional: añadir bases comunes como fallback
	    assets.addAll(SIMBOLOS_BASE_FIJOS); // p.ej. Set.of("BTC","ETH","TRX")

	    Set<String> symbols = new HashSet<>();
	    for (String base : assets) {
	        String b = base.trim().toUpperCase();
	        if (b.isBlank()) continue;
	        for (String q : QUOTES) {
	            if (b.equalsIgnoreCase(q)) continue;
	            String candidate = b + q;
	            if (tradables.contains(candidate)) symbols.add(candidate);
	        }
	    }
	    return symbols;
	}


}
