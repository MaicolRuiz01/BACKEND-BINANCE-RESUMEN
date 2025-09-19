package com.binance.web.BinanceAPI;

import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.SellDollars.SellDollarsDto;
import com.binance.web.Entity.Cliente;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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
    private static final String PRO_BASE = "https://api.solscan.io/v2";
    private static final String PUB_BASE = "https://public-api.solscan.io";
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

    private HttpEntity<Void> solscanEntity() {
        return new HttpEntity<>(commonHeaders());
    }

    private String pickBase() {
        return (solscanApiKey != null && !solscanApiKey.isBlank()) ? PRO_BASE : PUB_BASE;
    }

    /* ===================== RAW Solscan ===================== */

    public String getAccountRaw(String address) {
        String url = pickBase() + "/account?address=" + address;
        return restTemplate.exchange(url, HttpMethod.GET, solscanEntity(), String.class).getBody();
    }

    public String getAccountTokensRaw(String address) {
        String url = pickBase() + "/account/tokens?address=" + address + "&price=1";
        return restTemplate.exchange(url, HttpMethod.GET, solscanEntity(), String.class).getBody();
    }

    /** Intenta PRO -> PUBLIC. Si ambos devuelven 403, cae a Helius y construye un JSON compatible. */
    public String getSplTransfersRaw(String address, int limit) {
        int lim = (limit <= 0 ? 50 : limit);
        String proUrl = UriComponentsBuilder.fromHttpUrl(PRO_BASE + "/account/splTransfers")
                .queryParam("address", address)
                .queryParam("limit", lim)
                .toUriString();
        String pubUrl = UriComponentsBuilder.fromHttpUrl(PUB_BASE + "/account/splTransfers")
                .queryParam("address", address)
                .queryParam("limit", lim)
                .toUriString();

        try {
            System.out.println("➡️ Solscan splTransfers PRO URL=" + proUrl);
            return restTemplate.exchange(proUrl, HttpMethod.GET, solscanEntity(), String.class).getBody();
        } catch (HttpClientErrorException.Forbidden e) {
            System.out.println("🚫 403 Solscan PRO. Probando PUBLIC...");
            try {
                System.out.println("➡️ Solscan splTransfers PUBLIC URL=" + pubUrl);
                return restTemplate.exchange(pubUrl, HttpMethod.GET, solscanEntity(), String.class).getBody();
            } catch (HttpClientErrorException.Forbidden e2) {
                System.out.println("🚫 403 Solscan PUBLIC. Fallback a Helius si hay API key.");
                if (heliusApiKey != null && !heliusApiKey.isBlank()) {
                    return heliusTransfersAsSolscan(address, lim); // JSON “data”: [...]
                }
                // último recurso: evita romper el flujo
                return "{\"data\":[]}";
            } catch (Exception ex2) {
                System.out.println("⚠️ Error Solscan PUBLIC: " + ex2.getMessage());
                if (heliusApiKey != null && !heliusApiKey.isBlank()) {
                    return heliusTransfersAsSolscan(address, lim);
                }
                return "{\"data\":[]}";
            }
        } catch (Exception ex) {
            System.out.println("⚠️ Error Solscan PRO: " + ex.getMessage());
            if (heliusApiKey != null && !heliusApiKey.isBlank()) {
                return heliusTransfersAsSolscan(address, lim);
            }
            return "{\"data\":[]}";
        }
    }

    public String getSolTransfersRaw(String address, int limit) {
        String url = pickBase() + "/account/solTransfers?address=" + address + "&limit=" + limit;
        return restTemplate.exchange(url, HttpMethod.GET, solscanEntity(), String.class).getBody();
    }

    /** Intenta Solscan. Si 403, intenta Helius (POST /v0/transactions) para extraer el fee. */
    public JsonNode getTxDetails(String signature) {
        String url = pickBase() + "/transaction?tx=" + signature;
        try {
            String raw = restTemplate.exchange(url, HttpMethod.GET, solscanEntity(), String.class).getBody();
            return objectMapper.readTree(raw);
        } catch (HttpClientErrorException.Forbidden e) {
            if (heliusApiKey == null || heliusApiKey.isBlank()) return objectMapper.createObjectNode();
            try {
                HttpHeaders h = new HttpHeaders();
                h.setContentType(MediaType.APPLICATION_JSON);
                h.setAccept(List.of(MediaType.APPLICATION_JSON));
                String txUrl = HELIUS_BASE + "/v0/transactions?api-key=" + heliusApiKey.trim();
                ArrayNode body = objectMapper.createArrayNode();
                body.add(signature);
                HttpEntity<String> req = new HttpEntity<>(body.toString(), h);
                String raw = restTemplate.exchange(txUrl, HttpMethod.POST, req, String.class).getBody();
                ArrayNode arr = (ArrayNode) objectMapper.readTree(raw);
                if (arr.size() == 0) return objectMapper.createObjectNode();

                JsonNode first = arr.get(0);
                long fee = first.path("fee").asLong(0L);

                ObjectNode out = objectMapper.createObjectNode();
                ObjectNode meta = objectMapper.createObjectNode();
                meta.put("fee", fee);
                out.set("meta", meta);
                return out;
            } catch (Exception ignore) {
                return objectMapper.createObjectNode();
            }
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    /* ===================== Fallback Helius → formateo estilo Solscan ===================== */

    // Mints comunes → símbolo/decimales (extiende si usas otros tokens)
    private static final Map<String, String> MINT_TO_SYMBOL = Map.of(
        "EPjFWdd5AufqSSqeM2q9JV5xQ6G8TLVL4Qp7q4G4Q1k", "USDC", // USDC
        "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", "USDT"  // USDT (legacy)
    );
    private static final Map<String, Integer> SYMBOL_DECIMALS = Map.of(
        "USDC", 6,
        "USDT", 6
    );

    /** Devuelve un JSON con campo "data":[...] compatible con los parsers actuales. */
    private String heliusTransfersAsSolscan(String address, int limit) {
        try {
            String url = HELIUS_BASE + "/v0/addresses/" + address
                    + "/transactions?api-key=" + heliusApiKey.trim()
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
                        if (symbol == null || uiAmount == null || sig == null) continue;

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

    /* ===================== Negocio: balances y totales ===================== */

    public Map<String, Double> getBalancesByAsset(String address) {
        Map<String, Double> out = new HashMap<>();
        try {
            String accRaw = getAccountRaw(address);
            JsonNode acc = objectMapper.readTree(accRaw);
            long lamports = acc.path("lamports").asLong(acc.path("balance").asLong(0L));
            double sol = lamports / 1_000_000_000.0;
            if (sol > 0) out.put("SOL", sol);

            String tokRaw = getAccountTokensRaw(address);
            JsonNode tokens = objectMapper.readTree(tokRaw);
            JsonNode data = tokens.isArray() ? tokens : tokens.path("data");
            if (data != null && data.isArray()) {
                for (JsonNode t : data) {
                    String sym = pickUpper(
                        t.path("symbol").asText(null),
                        t.path("tokenSymbol").asText(null),
                        t.path("tokenName").asText(null)
                    );
                    if (sym == null) continue;

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
                            String ui = firstNonBlank(
                                t.path("tokenAmount").path("uiAmountString").asText(null),
                                t.path("uiAmountString").asText(null),
                                t.path("uiAmount").asText(null)
                            );
                            if (ui != null) qty = Double.parseDouble(ui);
                        }
                    } catch (Exception ignore) {}
                    if (qty > 0) out.merge(sym, qty, Double::sum);
                }
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Error obteniendo snapshot SOLANA: " + e.getMessage(), e);
        }
    }

    public double getTotalAssetUsd(String address) {
        Map<String, Double> balances = getBalancesByAsset(address);
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

    /* ===================== Parsers estilo Tron ===================== */

    public List<BuyDollarsDto> parseSplIncomingTransfers(
            String jsonResponse, String walletAddress, String accountName, Set<String> assignedIds
    ) {
        List<BuyDollarsDto> out = new ArrayList<>();
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.isArray() ? root : root.path("data");
            if (!data.isArray()) return out;

            for (JsonNode tx : data) {
                String to = pickLower(tx.path("dst").asText(null), tx.path("to").asText(null), tx.path("destination").asText(null));
                String sig = firstNonBlank(tx.path("txHash").asText(null), tx.path("signature").asText(null), tx.path("transactionHash").asText(null));
                String symbol = pickUpper(tx.path("symbol").asText(null), tx.path("tokenSymbol").asText(null));

                String rawValue = firstNonBlank(tx.path("changeAmount").asText(null), tx.path("amount").asText(null),
                        tx.path("tokenAmount").path("amount").asText(null));
                int dec = firstNonNeg(tx.path("decimals").asInt(-1), tx.path("tokenAmount").path("decimals").asInt(-1));
                long ts = firstNonZero(tx.path("blockTime").asLong(0L), tx.path("timeStamp").asLong(0L)) * 1000L;

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
                        String ui = firstNonBlank(tx.path("uiAmountString").asText(null),
                                tx.path("tokenAmount").path("uiAmountString").asText(null));
                        if (ui != null) qty = Double.parseDouble(ui);
                    }
                } catch (Exception ignore) {}

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

    public List<SellDollarsDto> parseSplOutgoingTransfers(
            String jsonResponse, String walletAddress, String accountName, Set<String> assignedIds, Map<String, Cliente> clientePorWallet
    ) {
        List<SellDollarsDto> out = new ArrayList<>();
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.isArray() ? root : root.path("data");
            if (!data.isArray()) return out;

            for (JsonNode tx : data) {
                String from = pickLower(tx.path("src").asText(null), tx.path("from").asText(null), tx.path("source").asText(null));
                String to = pickLower(tx.path("dst").asText(null), tx.path("to").asText(null), tx.path("destination").asText(null));
                String sig = firstNonBlank(tx.path("txHash").asText(null), tx.path("signature").asText(null), tx.path("transactionHash").asText(null));
                String symbol = pickUpper(tx.path("symbol").asText(null), tx.path("tokenSymbol").asText(null));
                String rawValue = firstNonBlank(tx.path("changeAmount").asText(null), tx.path("amount").asText(null),
                        tx.path("tokenAmount").path("amount").asText(null));
                int dec = firstNonNeg(tx.path("decimals").asInt(-1), tx.path("tokenAmount").path("decimals").asInt(-1));
                long ts = firstNonZero(tx.path("blockTime").asLong(0L), tx.path("timeStamp").asLong(0L)) * 1000L;

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
                        String ui = firstNonBlank(tx.path("uiAmountString").asText(null),
                                tx.path("tokenAmount").path("uiAmountString").asText(null));
                        if (ui != null) qty = Double.parseDouble(ui);
                    }
                } catch (Exception ignore) {}

                if (qty <= 0) continue;

                double feeSol = 0.0;
                try {
                    JsonNode txDetails = getTxDetails(sig);
                    long feeLamports = txDetails.path("meta").path("fee").asLong(txDetails.path("fee").asLong(0L));
                    feeSol = feeLamports / 1_000_000_000.0;
                } catch (Exception ignore) {}

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
                    if (c != null) dto.setClienteId(c.getId());
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

    /* ===================== Wrappers de alto nivel ===================== */

    public List<BuyDollarsDto> listIncomingToday(String walletAddress, String accountName, Set<String> assignedIds) {
        String raw = getSplTransfersRaw(walletAddress, 200);
        return parseSplIncomingTransfers(raw, walletAddress, accountName, assignedIds);
    }

    public List<SellDollarsDto> listOutgoingToday(
            String walletAddress, String accountName, Set<String> assignedIds, Map<String, Cliente> clientePorWallet
    ) {
        String raw = getSplTransfersRaw(walletAddress, 200);
        List<SellDollarsDto> out = parseSplOutgoingTransfers(raw, walletAddress, accountName, assignedIds, clientePorWallet);
        out.forEach(dto -> dto.setComision(0.0)); // nunca descuentas fee del token
        return out;
    }
}
