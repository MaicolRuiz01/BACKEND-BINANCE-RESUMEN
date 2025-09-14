package com.binance.web.BinanceAPI;



import com.binance.web.Entity.Cliente;
import com.binance.web.SellDollars.SellDollarsDto;
import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.BuyDollars.BuyDollarsDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SolscanService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Lee el token desde application.properties (igual que haces con Tron)
    @Value("${solscan.apiKey:}")
    private String solscanApiKey;

    // Para precios en USDT (SOL, etc.). Lo inyectamos para reutilizar tu misma lógica de precios.
    private final BinanceService binanceService;

    public SolscanService(BinanceService binanceService) {
        this.binanceService = binanceService;
    }

    /* ===================== Base URLs (Pro y Pública) ===================== */
    // Pro (con API key) – v2
    private static final String PRO_BASE = "https://api.solscan.io/v2";
    // Pública (por si no hay API key / fallback)
    private static final String PUB_BASE = "https://public-api.solscan.io";

    /* ===================== Helpers de headers ===================== */

    private HttpEntity<Void> solscanEntity() {
        HttpHeaders h = new HttpHeaders();
        // Solscan Pro acepta "Authorization: Bearer <token>".
        // También hay instalaciones que aceptan 'token:<jwt>'. Enviamos ambos inofensivamente.
        if (solscanApiKey != null && !solscanApiKey.isBlank()) {
            h.set("Authorization", "Bearer " + solscanApiKey);
            h.set("token", solscanApiKey);
        }
        return new HttpEntity<>(h);
    }

    private String pickBase() {
        return (solscanApiKey != null && !solscanApiKey.isBlank()) ? PRO_BASE : PUB_BASE;
    }

    /* ===================== Endpoints RAW (tolerantes al esquema) ===================== */

    /** Info de cuenta (lamports, owner, etc.). */
    public String getAccountRaw(String address) {
        String url = pickBase() + "/account?address=" + address;
        return restTemplate.exchange(url, HttpMethod.GET, solscanEntity(), String.class).getBody();
    }

    /** Tokens SPL de la cuenta; con precio cuando está disponible. */
    public String getAccountTokensRaw(String address) {
        // muchos despliegues exponen /account/tokens?address=...&price=1
        String url = pickBase() + "/account/tokens?address=" + address + "&price=1";
        return restTemplate.exchange(url, HttpMethod.GET, solscanEntity(), String.class).getBody();
    }

    /** Transferencias SPL (USDC, etc.) de una cuenta. */
    public String getSplTransfersRaw(String address, int limit) {
        // en algunos esquemas: /account/splTransfers?address=...&limit=...
        String url = pickBase() + "/account/splTransfers?address=" + address + "&limit=" + limit;
        return restTemplate.exchange(url, HttpMethod.GET, solscanEntity(), String.class).getBody();
    }

    /** Transferencias nativas SOL de una cuenta. */
    public String getSolTransfersRaw(String address, int limit) {
        // en algunos esquemas: /account/solTransfers?address=...&limit=...
        String url = pickBase() + "/account/solTransfers?address=" + address + "&limit=" + limit;
        return restTemplate.exchange(url, HttpMethod.GET, solscanEntity(), String.class).getBody();
    }

    /** Detalle de transacción por signature. Útil para leer fee en SOL (lamports). */
    public JsonNode getTxDetails(String signature) {
        // común: /transaction?tx=<signature>
        String url = pickBase() + "/transaction?tx=" + signature;
        try {
            String raw = restTemplate.exchange(url, HttpMethod.GET, solscanEntity(), String.class).getBody();
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    /* ===================== Negocio: balances y totales ===================== */

    /** Snapshot: símbolo → cantidad (normalizada) de una wallet de Solana. Incluye SOL y SPL. */
    public Map<String, Double> getBalancesByAsset(String address) {
        Map<String, Double> out = new HashMap<>();

        try {
            // 1) SOL nativo (lamports)
            String accRaw = getAccountRaw(address);
            JsonNode acc = objectMapper.readTree(accRaw);
            // distintos esquemas: lamports en "lamports" o "balance"
            long lamports = acc.path("lamports").asLong(acc.path("balance").asLong(0L));
            double sol = lamports / 1_000_000_000.0; // 1 SOL = 1e9 lamports
            if (sol > 0) out.put("SOL", sol);

            // 2) SPL tokens
            String tokRaw = getAccountTokensRaw(address);
            JsonNode tokens = objectMapper.readTree(tokRaw);
            JsonNode data = tokens.isArray() ? tokens : tokens.path("data"); // pro/público varía

            if (data != null && data.isArray()) {
                for (JsonNode t : data) {
                    // símbolos pueden venir en "symbol" o "tokenSymbol"
                    String sym = pickUpper(
                            t.path("symbol").asText(null),
                            t.path("tokenSymbol").asText(null),
                            t.path("tokenName").asText(null)
                    );
                    if (sym == null) continue;

                    // cantidad: puede venir como tokenAmount.amount + decimals, o balance/uiAmount
                    int dec = firstNonNeg(
                            t.path("decimals").asInt(-1),
                            t.path("tokenAmount").path("decimals").asInt(-1)
                    );
                    String amountStr = firstNonBlank(
                            t.path("tokenAmount").path("amount").asText(null),
                            t.path("balance").asText(null),
                            t.path("quantity").asText(null)
                    );

                    double qty = 0.0;
                    try {
                        if (amountStr != null && dec >= 0) {
                            qty = Double.parseDouble(amountStr) / Math.pow(10, dec);
                        } else {
                            // fallback: uiAmount / uiAmountString
                            String ui = firstNonBlank(
                                    t.path("tokenAmount").path("uiAmountString").asText(null),
                                    t.path("uiAmountString").asText(null),
                                    t.path("uiAmount").asText(null)
                            );
                            if (ui != null) qty = Double.parseDouble(ui);
                        }
                    } catch (Exception ignore) { /* tolerante */ }

                    if (qty > 0) out.merge(sym, qty, Double::sum);
                }
            }

            return out;
        } catch (Exception e) {
            throw new RuntimeException("Error obteniendo snapshot SOLANA: " + e.getMessage(), e);
        }
    }

    /** Total en USD de una wallet Solana (usa precios USDT vía tu BinanceService). */
    public double getTotalAssetUsd(String address) {
        Map<String, Double> balances = getBalancesByAsset(address);
        if (balances.isEmpty()) return 0.0;

        double total = 0.0;
        for (Map.Entry<String, Double> e : balances.entrySet()) {
            String sym = e.getKey();
            double qty = e.getValue();
            double px;
            if ("USDT".equalsIgnoreCase(sym) || "USDC".equalsIgnoreCase(sym)) {
                px = 1.0;
            } else {
                Double p = binanceService.getPriceInUSDT(sym); // misma ruta que ya usas
                px = (p != null) ? p : 0.0;
            }
            total += qty * px;
        }
        return total;
    }

    /* ===================== Parsers estilo Tron (entradas/salidas SPL) ===================== */
    // Nota: Solana maneja "lamports" (1e9 = 1 SOL). Fees se pagan en SOL.

    /** Entradas SPL → BuyDollarsDto (hoy). */
    public List<BuyDollarsDto> parseSplIncomingTransfers(
            String jsonResponse,
            String walletAddress,
            String accountName,
            Set<String> assignedIds
    ) {
        List<BuyDollarsDto> out = new ArrayList<>();
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.isArray() ? root : root.path("data");
            if (!data.isArray()) return out;

            for (JsonNode tx : data) {
                // esquemas típicos:
                String to = pickLower(
                        tx.path("dst").asText(null),
                        tx.path("to").asText(null),
                        tx.path("destination").asText(null)
                );
                String sig = firstNonBlank(
                        tx.path("txHash").asText(null),
                        tx.path("signature").asText(null),
                        tx.path("transactionHash").asText(null)
                );
                String symbol = pickUpper(
                        tx.path("symbol").asText(null),
                        tx.path("tokenSymbol").asText(null)
                );

                String rawValue = firstNonBlank(
                        tx.path("changeAmount").asText(null),
                        tx.path("amount").asText(null),
                        tx.path("tokenAmount").path("amount").asText(null)
                );
                int dec = firstNonNeg(
                        tx.path("decimals").asInt(-1),
                        tx.path("tokenAmount").path("decimals").asInt(-1)
                );

                long ts = firstNonZero(
                        tx.path("blockTime").asLong(0L),
                        tx.path("timeStamp").asLong(0L)
                ) * 1000L; // muchos endpoints devuelven segundos

                if (to == null || sig == null || symbol == null) continue;
                if (!to.equalsIgnoreCase(walletAddress)) continue;
                if (assignedIds.contains(sig)) continue;

                LocalDate fecha = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota")).toLocalDate();
                if (!fecha.isEqual(hoy)) continue;

                double qty = 0.0;
                try {
                    if (rawValue != null && dec >= 0) {
                        qty = Double.parseDouble(rawValue) / Math.pow(10, dec);
                    } else {
                        String ui = firstNonBlank(
                                tx.path("uiAmountString").asText(null),
                                tx.path("tokenAmount").path("uiAmountString").asText(null)
                        );
                        if (ui != null) qty = Double.parseDouble(ui);
                    }
                } catch (Exception ignore) { }

                if (qty <= 0) continue;

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

    /** Salidas SPL → SellDollarsDto (hoy) con comisión en SOL. */
    public List<SellDollarsDto> parseSplOutgoingTransfers(
            String jsonResponse,
            String walletAddress,
            String accountName,
            Set<String> assignedIds,
            Map<String, Cliente> clientePorWallet // si quieres mapear destino→cliente
    ) {
        List<SellDollarsDto> out = new ArrayList<>();
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.isArray() ? root : root.path("data");
            if (!data.isArray()) return out;

            for (JsonNode tx : data) {
                String from = pickLower(
                        tx.path("src").asText(null),
                        tx.path("from").asText(null),
                        tx.path("source").asText(null)
                );
                String to = pickLower(
                        tx.path("dst").asText(null),
                        tx.path("to").asText(null),
                        tx.path("destination").asText(null)
                );
                String sig = firstNonBlank(
                        tx.path("txHash").asText(null),
                        tx.path("signature").asText(null),
                        tx.path("transactionHash").asText(null)
                );
                String symbol = pickUpper(
                        tx.path("symbol").asText(null),
                        tx.path("tokenSymbol").asText(null)
                );

                String rawValue = firstNonBlank(
                        tx.path("changeAmount").asText(null),
                        tx.path("amount").asText(null),
                        tx.path("tokenAmount").path("amount").asText(null)
                );
                int dec = firstNonNeg(
                        tx.path("decimals").asInt(-1),
                        tx.path("tokenAmount").path("decimals").asInt(-1)
                );

                long ts = firstNonZero(
                        tx.path("blockTime").asLong(0L),
                        tx.path("timeStamp").asLong(0L)
                ) * 1000L;

                if (from == null || sig == null || symbol == null) continue;
                if (!from.equalsIgnoreCase(walletAddress)) continue;
                if (assignedIds.contains(sig)) continue;

                LocalDate fecha = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota")).toLocalDate();
                if (!fecha.isEqual(hoy)) continue;

                double qty = 0.0;
                try {
                    if (rawValue != null && dec >= 0) {
                        qty = Double.parseDouble(rawValue) / Math.pow(10, dec);
                    } else {
                        String ui = firstNonBlank(
                                tx.path("uiAmountString").asText(null),
                                tx.path("tokenAmount").path("uiAmountString").asText(null)
                        );
                        if (ui != null) qty = Double.parseDouble(ui);
                    }
                } catch (Exception ignore) { }

                if (qty <= 0) continue;

                // Comisión en SOL: consultar la transacción y leer "fee" en lamports
                double feeSol = 0.0;
                try {
                    JsonNode txDetails = getTxDetails(sig);
                    long feeLamports = txDetails.path("meta").path("fee").asLong(
                            txDetails.path("fee").asLong(0L)
                    );
                    feeSol = feeLamports / 1_000_000_000.0;
                } catch (Exception ignore) { }

                SellDollarsDto dto = new SellDollarsDto();
                dto.setIdWithdrawals(sig);
                dto.setNameAccount(accountName);
                dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota")));
                dto.setDollars(qty);
                dto.setCryptoSymbol(symbol);
                dto.setComision(feeSol);   // comisión real en SOL
                dto.setTasa(0.0);
                dto.setPesos(0.0);

                // si enviaste a la wallet de un cliente, asígnalo
                if (to != null && clientePorWallet != null) {
                    Cliente c = clientePorWallet.get(to.trim().toLowerCase());
                    if (c != null) dto.setClienteId(c.getId());
                }

                out.add(dto);
            }
            return out;
        } catch (Exception e) {
            return out;
        }
    }

    /* ===================== Utils internos ===================== */

    private static String pickUpper(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v.trim().toUpperCase();
        return null;
    }
    private static String pickLower(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v.trim().toLowerCase();
        return null;
    }
    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }
    private static int firstNonNeg(int... vals) {
        for (int v : vals) if (v >= 0) return v;
        return -1;
    }
    private static long firstNonZero(long... vals) {
        for (long v : vals) if (v != 0L) return v;
        return 0L;
    }
    
    /** Wrapper para traer transferencias SPL y devolver BUYs de HOY. */
    public List<BuyDollarsDto> listIncomingToday(
            String walletAddress,
            String accountName,
            Set<String> assignedIds
    ) {
        // pide las últimas (sube el limit si lo necesitas)
        String raw = getSplTransfersRaw(walletAddress, 200);
        // reutiliza el parser que ya tienes
        return parseSplIncomingTransfers(raw, walletAddress, accountName, assignedIds);
    }

    /** Wrapper para traer transferencias SPL y devolver SELLs de HOY. */
    public List<SellDollarsDto> listOutgoingToday(
            String walletAddress,
            String accountName,
            Set<String> assignedIds,
            Map<String, Cliente> clientePorWallet
    ) {
        String raw = getSplTransfersRaw(walletAddress, 200);
        List<SellDollarsDto> out =
            parseSplOutgoingTransfers(raw, walletAddress, accountName, assignedIds, clientePorWallet);

        // IMPORTANTE para tu flujo actual:
        // En tu SellDollarsService descuentas (dólares + comisión) del MISMO símbolo.
        // En Solana la comisión es en SOL. Para no restar SOL a un token (p.ej. USDC),
        // seteamos comisión = 0 aquí. Si luego quieres descontar el fee en SOL,
        // hazlo aparte en SellDollarsService (te digo cómo si lo necesitas).
        out.forEach(dto -> dto.setComision(0.0));

        return out;
    }
}

