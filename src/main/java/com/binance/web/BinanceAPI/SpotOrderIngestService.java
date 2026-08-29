package com.binance.web.BinanceAPI;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.SpotOrder;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.SpotOrderRepository;
import com.binance.web.model.CryptoPendienteDto;
import com.binance.web.service.AccountBinanceService;
import com.binance.web.service.CryptoAverageRateService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SpotOrderIngestService {

    private final BinanceService binanceService;
    private final AccountBinanceRepository accountRepo;
    private final SpotOrderRepository spotOrderRepo;
    private final AccountBinanceService accountService;
    private final CryptoAverageRateService cryptoAverageRateService;
    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

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


    public int importarTodasLasCuentasAuto(int limitPorSimbolo) {

        // Antes acá se bloqueaba TODA la importación si alguna cripto no tenía tasa promedio
        // inicial configurada. Se quitó junto con el sistema de tasas por cripto: el negocio
        // maneja solo USDT y ya no se lleva una tasa por cada moneda.
        //
        // Era importante quitarlo: al dejar de calcular esas tasas, esta validación habría
        // bloqueado la importación de órdenes spot para siempre, y sin ningún aviso claro.
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
            final String apiKey = acc.getApiKey();
            final String secret = acc.getApiSecret();

            String raw = binanceService.getOrderHistory(apiKey, secret, symbol, limit);
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonArray()) return 0;

            String quote = detectQuote(symbol);
            String base  = symbol.substring(0, symbol.length() - quote.length());

            int count = 0;
            LocalDate hoy = LocalDate.now(ZONE_BOGOTA);
            
            for (JsonElement el : parsed.getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                if (!"FILLED".equalsIgnoreCase(o.get("status").getAsString())) continue;

                long orderId = o.get("orderId").getAsLong();
                if (spotOrderRepo.existsByCuentaBinanceAndIdOrdenBinance(acc, orderId)) continue;

                String side     = o.get("side").getAsString();
                double execBase = o.get("executedQty").getAsDouble();
                double execQ    = o.get("cummulativeQuoteQty").getAsDouble();

                // Fills
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
                        qtyBaseSum    += qty;
                        notionalQuote += quoteQty;

                        String cAsset = f.get("commissionAsset").getAsString();
                        double cQty   = f.get("commission").getAsDouble();
                        feeByAsset.merge(cAsset, cQty, Double::sum);

                        if (f.has("time")) {
                            lastFillTs = Math.max(lastFillTs, f.get("time").getAsLong());
                        }
                    }
                }

                double avgPrice = qtyBaseSum > 0
                        ? notionalQuote / qtyBaseSum
                        : (execBase > 0 ? execQ / execBase : 0.0);

                double feeUsdt = 0.0;
                for (var e : feeByAsset.entrySet()) {
                    double px = getUsdtPriceCached(e.getKey());
                    feeUsdt += e.getValue() * Math.max(px, 0.0);
                }

                long ts = (lastFillTs > 0
                        ? lastFillTs
                        : (o.has("updateTime") ? o.get("updateTime").getAsLong() : o.get("time").getAsLong()));

                LocalDateTime fechaOp = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(ts),
                        ZONE_BOGOTA
                );
                
             // 🔴 1) SOLO ÓRDENES DE HOY
                if (!fechaOp.toLocalDate().isEqual(hoy)) {
                    // Si no es del día actual → la ignoramos totalmente
                    continue;
                }

                // (Se quitó la validación de tasa inicial por cripto: ya no se llevan tasas
                //  promedio por moneda, el negocio maneja solo USDT.)

             // ✅ Crear y guardar la orden
                SpotOrder so = new SpotOrder();
                so.setCuentaBinance(acc);
                so.setIdOrdenBinance(orderId);
                so.setIdOrdenCliente(o.get("clientOrderId").getAsString());
                so.setSimbolo(symbol);

                String tipoOperacion = side.equalsIgnoreCase("BUY") ? "COMPRA" : "VENTA";
                so.setTipoOperacion(tipoOperacion);

                so.setCripto(base);
                so.setCantidadCripto(execBase);
                so.setTotalUsdt(execQ);
                so.setTasaUsdt(avgPrice);
                so.setComisionUsdt(feeUsdt);
                so.setFechaOperacion(fechaOp);
                so.setDetalleBinanceJson(new Gson().toJson(feeByAsset));

                spotOrderRepo.save(so);

                // (Ya no se recalcula tasa promedio por cripto en cada compra: solo USDT.)

                // ✅ 5) Ajustar saldos internos
                applyDeltas(acc, base, quote, side, execBase, execQ, feeByAsset);

                count++;
            }
            return count;
        } catch (Exception e) {
            throw new RuntimeException("Import " + symbol + " (" + acc.getName() + "): " + e.getMessage(), e);
        }
    }


    /**
     * Antes ajustaba el saldo cripto interno con los deltas de cada orden spot.
     *
     * Ya no hace nada: el saldo interno se eliminó. Todo se lee en vivo de Binance, que es la
     * fuente real, así que llevar una contabilidad paralela era trabajo de más que además se
     * descuadraba cuando alguna operación no se importaba bien.
     *
     * Se conserva el método (vacío) en vez de borrar las llamadas para no alterar el flujo de
     * la importación de órdenes, que sigue guardando la orden igual que siempre.
     */
    private void applyDeltas(AccountBinance acc, String base, String quote, String side,
                             double execBase, double execQuote, Map<String, Double> feeByAsset) {
        // Sin efecto: el saldo cripto interno ya no se lleva.
    }
}
