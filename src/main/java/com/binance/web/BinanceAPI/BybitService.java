package com.binance.web.BinanceAPI;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.binance.web.util.HttpClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Lectura de saldos de una cuenta Bybit (API V5), SOLO LECTURA.
 *
 * Diseño defensivo: NUNCA lanza hacia arriba de forma que tumbe el sync de saldos.
 *  - RestTemplate con timeouts (HttpClientFactory) → una API colgada no bloquea el hilo.
 *  - Cada billetera (Unified / Funding) se consulta por separado y en su propio try/catch:
 *    si una falla, se usa lo que haya de la otra y se devuelve lo que se pudo leer.
 *  - Si faltan credenciales o algo revienta, devuelve un mapa vacío (saldo 0), no una excepción.
 *
 * Firma V5 (GET): HMAC-SHA256( timestamp + apiKey + recvWindow + queryString ) en hex.
 */
@Slf4j
@Service
public class BybitService {

    private static final String BASE = "https://api.bybit.com";
    private static final String RECV_WINDOW = "5000";

    // RestTemplate CON timeouts (igual que el resto de servicios externos).
    private final RestTemplate restTemplate = HttpClientFactory.timed();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Saldo por moneda de la cuenta Bybit, sumando la billetera Unified (trading) y la
     * Funding (fondos). Cantidades CRUDAS (ej. USDT reales), misma escala que Binance/TRON.
     * Devuelve mapa vacío si no hay credenciales o si todo falla (nunca null, nunca throw).
     */
    public Map<String, Double> getBalancesByAsset(String apiKey, String apiSecret) {
        Map<String, Double> out = new HashMap<>();
        if (apiKey == null || apiSecret == null || apiKey.isBlank() || apiSecret.isBlank()) {
            log.warn("[Bybit] Cuenta sin apiKey/apiSecret → saldo 0.");
            return out;
        }

        // 1) Billetera UNIFIED (trading).
        try {
            leerUnified(apiKey, apiSecret, out);
        } catch (Exception e) {
            log.warn("[Bybit] No se pudo leer Unified: {}", e.getMessage());
        }

        // 2) Billetera FUNDING (fondos). Se suma a lo anterior.
        try {
            leerFunding(apiKey, apiSecret, out);
        } catch (Exception e) {
            log.warn("[Bybit] No se pudo leer Funding: {}", e.getMessage());
        }

        return out;
    }

    /**
     * Hashes on-chain (txID) de los RETIROS recientes de esta cuenta Bybit (últimos 7 días).
     * Es la base de la detección REAL de traspasos: si un depósito entrante trae un hash que
     * está en esta lista, ese depósito salió de ESTA cuenta Bybit → es un traspaso interno,
     * no una compra externa. Reemplaza el enfoque de "wallets hardcodeadas" (Bybit rota wallets).
     * Defensivo: si falla, devuelve lista vacía (nunca throw, nunca null).
     */
    public List<String> getWithdrawalTxIds(String apiKey, String apiSecret) {
        List<String> out = new ArrayList<>();
        if (apiKey == null || apiSecret == null || apiKey.isBlank() || apiSecret.isBlank()) return out;
        try {
            long end = System.currentTimeMillis();
            long start = end - 7L * 24 * 3600 * 1000; // últimos 7 días
            String query = "startTime=" + start + "&endTime=" + end + "&limit=50";
            JsonNode root = signedGet("/v5/asset/withdraw/query-record", query, apiKey, apiSecret);
            for (JsonNode w : root.path("result").path("rows")) {
                String txId = w.path("txID").asText(null);
                if (txId != null && !txId.isBlank()) out.add(txId.trim());
            }
        } catch (Exception e) {
            log.warn("[Bybit] No se pudieron leer los retiros: {}", e.getMessage());
        }
        return out;
    }

    /** GET /v5/account/wallet-balance?accountType=UNIFIED → result.list[].coin[] { coin, walletBalance }. */
    private void leerUnified(String apiKey, String apiSecret, Map<String, Double> out) throws Exception {
        JsonNode root = signedGet("/v5/account/wallet-balance", "accountType=UNIFIED", apiKey, apiSecret);
        for (JsonNode acc : root.path("result").path("list")) {
            for (JsonNode c : acc.path("coin")) {
                acumular(out, c.path("coin").asText(null), c.path("walletBalance").asText(null));
            }
        }
    }

    /**
     * GET /v5/asset/transfer/query-account-coins-balance?accountType=FUND
     *  → result.balance[] { coin, walletBalance | transferBalance }.
     */
    private void leerFunding(String apiKey, String apiSecret, Map<String, Double> out) throws Exception {
        JsonNode root = signedGet("/v5/asset/transfer/query-account-coins-balance", "accountType=FUND", apiKey, apiSecret);
        for (JsonNode b : root.path("result").path("balance")) {
            String qty = b.hasNonNull("walletBalance") ? b.path("walletBalance").asText(null)
                       : b.path("transferBalance").asText(null);
            acumular(out, b.path("coin").asText(null), qty);
        }
    }

    /** Suma una (moneda, cantidad) al mapa si son válidas y > 0. */
    private void acumular(Map<String, Double> out, String coin, String qtyStr) {
        if (coin == null || coin.isBlank() || qtyStr == null || qtyStr.isBlank()) return;
        double qty;
        try { qty = Double.parseDouble(qtyStr); } catch (NumberFormatException e) { return; }
        if (qty <= 0) return;
        out.merge(coin.trim().toUpperCase(), qty, Double::sum);
    }

    /** GET firmado a Bybit V5. Lanza si retCode != 0 (lo captura el llamador). */
    private JsonNode signedGet(String path, String query, String apiKey, String apiSecret) throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());
        String firma = hmacSha256Hex(apiSecret, ts + apiKey + RECV_WINDOW + query);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-BAPI-API-KEY", apiKey);
        headers.set("X-BAPI-TIMESTAMP", ts);
        headers.set("X-BAPI-RECV-WINDOW", RECV_WINDOW);
        headers.set("X-BAPI-SIGN", firma);

        String url = BASE + path + (query.isBlank() ? "" : "?" + query);
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        JsonNode root = mapper.readTree(resp.getBody() == null ? "{}" : resp.getBody());
        int retCode = root.path("retCode").asInt(-1);
        if (retCode != 0) {
            throw new RuntimeException("retCode=" + retCode + " retMsg=" + root.path("retMsg").asText(""));
        }
        return root;
    }

    private String hmacSha256Hex(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
