package com.binance.web.BinanceAPI;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

 // solo cotizamos en USDT o USDC
    private static final List<String> QUOTES = List.of("USDT","USDC");

    // solo consideramos TRX como base frecuente
    private static final Set<String> BASE_WHITELIST = Set.of("TRX");

    // por si hoy no hay TRX en balance, igual lo consultamos
    private static final Set<String> SIMBOLOS_BASE_FIJOS = BASE_WHITELIST;

    
 // 🔹 caché simple por ejecución del servicio
    private final Map<String, Double> priceCache = new ConcurrentHashMap<>();

    private double getUsdtPriceCached(String asset) {
        String a = asset == null ? "" : asset.toUpperCase();
        if ("USDT".equals(a) || "USDC".equals(a)) return 1.0;
        return priceCache.computeIfAbsent(a, x -> {
            try { return binanceService.getPriceInUsdt(x); } catch (Exception e) { return 0.0; }
        });
    }

    private String detectQuote(String s) {
        String u = s.toUpperCase();
        for (String q : QUOTES) if (u.endsWith(q)) return q;
        throw new RuntimeException("Símbolo no soportado (quote): " + s);
    }

    /** Deducción dinámica super-reducida: solo TRX como base, quotes USDT/USDC */
    private Set<String> resolverSimbolosParaCuenta(AccountBinance acc) {
        Set<String> bases = new HashSet<>();
        try {
            bases.addAll(binanceService.getNonZeroAssets(acc.getName())); // puede fallar (401)
        } catch (RuntimeException e) {
            System.err.println("⚠️ No pude leer balances de " + acc.getName() + ": " + e.getMessage());
        }

        // nos quedamos solo con TRX (evita DOGE, SHIB, etc.)
        bases.retainAll(BASE_WHITELIST);

        // y aseguramos TRX aunque el balance sea 0 hoy
        bases.addAll(SIMBOLOS_BASE_FIJOS);

        Set<String> symbols = new HashSet<>();
        for (String base : bases) {
            String b = base == null ? "" : base.trim().toUpperCase();
            if (b.isBlank()) continue;
            for (String q : QUOTES) {
                if (b.equalsIgnoreCase(q)) continue; // evita USDTUSDT, USDCUSDC
                symbols.add(b + q);                  // TRXUSDT, TRXUSDC
            }
        }
        return symbols;
    }


    /** Importa TODO para todas las cuentas BINANCE. */
    public int importarTodasLasCuentasAuto(int limitPorSimbolo) {
        int total = 0;
        for (AccountBinance acc : accountRepo.findByTipo("BINANCE")) {
            Set<String> symbols = resolverSimbolosParaCuenta(acc);
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
        for (String s : symbols) inserted += importSymbol(acc, s.toUpperCase(), limit);
        return inserted;
    }

    /** Importa un símbolo (idempotente): guarda FILLED, calcula fees/avg y ajusta balance. */
    private int importSymbol(AccountBinance acc, String symbol, int limit) {
        try {
            // ✅ AQUÍ sí puedes obtener credenciales del 'acc'
            final String apiKey = acc.getApiKey();
            final String secret = acc.getApiSecret();

            // Usa las sobrecargas nuevas (si ya las creaste). Si no, deja tus métodos viejos con accountName.
            String raw = binanceService.getOrderHistory(apiKey, secret, symbol, limit);
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonArray()) return 0;

            String quote = detectQuote(symbol);
            String base  = symbol.substring(0, symbol.length() - quote.length());

            int count = 0;
            for (JsonElement el : parsed.getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                if (!"FILLED".equalsIgnoreCase(o.get("status").getAsString())) continue;

                long orderId = o.get("orderId").getAsLong();
                if (spotOrderRepo.existsByCuentaBinanceAndIdOrdenBinance(acc, orderId)) continue;


                String side     = o.get("side").getAsString();
                double execBase = o.get("executedQty").getAsDouble();
                double execQ    = o.get("cummulativeQuoteQty").getAsDouble();

                // Fills (credenciales aquí también)
                String fillsRaw = binanceService.getMyTradesByOrder(apiKey, secret, symbol, orderId);
                Map<String, Double> feeByAsset = new HashMap<>();
                double notionalQuote = 0.0, qtyBaseSum = 0.0;
                long lastFillTs = 0L;

                JsonElement fillsParsed = JsonParser.parseString(fillsRaw);
                if (fillsParsed.isJsonArray()) {
                    for (JsonElement fe : fillsParsed.getAsJsonArray()) {
                        JsonObject f = fe.getAsJsonObject();
                        double qty      = f.get("qty").getAsDouble();
                        double quoteQty = f.get("quoteQty").getAsDouble();
                        qtyBaseSum   += qty;
                        notionalQuote += quoteQty;

                        String cAsset  = f.get("commissionAsset").getAsString();
                        double cQty    = f.get("commission").getAsDouble();
                        feeByAsset.merge(cAsset, cQty, Double::sum);

                        if (f.has("time")) lastFillTs = Math.max(lastFillTs, f.get("time").getAsLong());
                    }
                }
                double avgPrice = qtyBaseSum > 0 ? notionalQuote / qtyBaseSum : (execBase > 0 ? execQ/execBase : 0.0);

                double feeUsdt = 0.0;
                for (var e : feeByAsset.entrySet()) {
                	double px = getUsdtPriceCached(e.getKey());
                	feeUsdt += e.getValue() * Math.max(px, 0.0);

                }

                long ts = (lastFillTs > 0 ? lastFillTs
                        : (o.has("updateTime") ? o.get("updateTime").getAsLong() : o.get("time").getAsLong()));

                SpotOrder so = new SpotOrder();
                so.setCuentaBinance(acc);
                so.setIdOrdenBinance(orderId);
                so.setIdOrdenCliente(o.get("clientOrderId").getAsString());
                so.setSimbolo(symbol);

                // BUY → COMPRA,  SELL → VENTA 
                String tipoOperacion = side.equalsIgnoreCase("BUY") ? "COMPRA" : "VENTA";
                so.setTipoOperacion(tipoOperacion);

                // base = TRX (o BTC o lo que sea)
                so.setCripto(base);

                // executedQty → cantidad de cripto comprada/vendida
                so.setCantidadCripto(execBase);

                // cummulativeQuoteQty → total en USDT
                so.setTotalUsdt(execQ);

                // avgPrice calculado arriba
                so.setTasaUsdt(avgPrice);

                // comisión final convertida a USDT
                so.setComisionUsdt(feeUsdt);

                // fecha real de ejecución
                so.setFechaOperacion(
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota"))
                );

                // JSON auditoría
                so.setDetalleBinanceJson(new Gson().toJson(feeByAsset));

                spotOrderRepo.save(so);


                applyDeltas(acc, base, quote, side, execBase, execQ, feeByAsset);
                count++;
            }
            return count;
        } catch (Exception e) {
            throw new RuntimeException("Import " + symbol + " (" + acc.getName() + "): " + e.getMessage(), e);
        }
    }

    private void applyDeltas(AccountBinance acc, String base, String quote, String side,
                             double execBase, double execQuote, Map<String, Double> feeByAsset) {
        double feeBase  = feeByAsset.getOrDefault(base, 0.0);
        double feeQuote = feeByAsset.getOrDefault(quote, 0.0);

        if ("BUY".equalsIgnoreCase(side)) {
            accountService.updateOrCreateCryptoBalance(acc.getId(), base,  +execBase - feeBase);
            accountService.updateOrCreateCryptoBalance(acc.getId(), quote, -execQuote - feeQuote);
        } else {
            accountService.updateOrCreateCryptoBalance(acc.getId(), base,  -execBase - feeBase);
            accountService.updateOrCreateCryptoBalance(acc.getId(), quote, +execQuote - feeQuote);
        }

        for (var e : feeByAsset.entrySet()) {
            String asset = e.getKey();
            if (asset.equalsIgnoreCase(base) || asset.equalsIgnoreCase(quote)) continue;
            accountService.updateOrCreateCryptoBalance(acc.getId(), asset, -e.getValue());
        }
    }
}
