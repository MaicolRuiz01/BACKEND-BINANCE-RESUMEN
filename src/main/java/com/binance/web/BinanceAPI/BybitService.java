package com.binance.web.BinanceAPI;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.binance.web.model.BuyDollarsDto;
import com.binance.web.model.SellDollarsDto;
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

    /**
     * Depósitos entrantes de HOY a esta cuenta Bybit (on-chain, status=success), en el mismo
     * formato genérico que usa TronScan para alimentar "registrar compras automáticamente".
     * Excluye depósitos cuyo remitente sea una de NUESTRAS propias wallets registradas
     * (eso es un traspaso interno, no una compra — igual que TronScanService con "propias").
     * Defensivo: nunca lanza, nunca null.
     */
    public List<BuyDollarsDto> getIncomingDeposits(String apiKey, String apiSecret, String accountName,
            Set<String> assignedIds, Set<String> ownAddresses) {
        return getIncomingDeposits(apiKey, apiSecret, accountName, assignedIds, ownAddresses,
                LocalDate.now(ZoneId.of("America/Bogota")));
    }

    /** Igual que arriba pero permitiendo elegir la fecha DESDE (inclusive) — para importaciones
     *  manuales de días anteriores (prueba/reproceso). El automático usa siempre HOY. */
    public List<BuyDollarsDto> getIncomingDeposits(String apiKey, String apiSecret, String accountName,
            Set<String> assignedIds, Set<String> ownAddresses, LocalDate desde) {
        List<BuyDollarsDto> out = new ArrayList<>();
        if (apiKey == null || apiSecret == null || apiKey.isBlank() || apiSecret.isBlank()) {
            log.warn("[Bybit][DIAG] Depósitos {}: cuenta sin apiKey/apiSecret → no se consulta.", accountName);
            return out;
        }

        Set<String> propias = normalizarTodas(ownAddresses);
        ZoneId zona = ZoneId.of("America/Bogota");

        try {
            long end = System.currentTimeMillis();
            long start = desde.atStartOfDay(zona).toInstant().toEpochMilli();
            String query = "startTime=" + start + "&endTime=" + end + "&limit=50";
            JsonNode root = signedGet("/v5/asset/deposit/query-record", query, apiKey, apiSecret);

            JsonNode rows = root.path("result").path("rows");
            log.info("[Bybit][DIAG] Depósitos {}: Bybit devolvió {} fila(s) en la ventana de hoy.",
                    accountName, rows.size());
            for (JsonNode r : rows) {
                int st = r.path("status").asInt(0);
                if (st != 3) { // 3 = success
                    log.info("[Bybit][DIAG] Depósito omitido ({}) → status={} (no success)", accountName, st);
                    continue;
                }
                String txId = r.path("txID").asText(null);
                if (txId == null || txId.isBlank()) { log.info("[Bybit][DIAG] Depósito omitido ({}) → sin txID", accountName); continue; }
                if (assignedIds != null && assignedIds.contains(txId)) { log.info("[Bybit][DIAG] Depósito omitido ({}) → ya registrado txID={}", accountName, txId); continue; }

                String from = r.path("fromAddress").asText(null);
                if (from != null && !from.isBlank() && propias.contains(normalizar(from))) {
                    log.info("[Bybit][DIAG] Depósito omitido ({}) → viene de wallet propia (traspaso) from={}", accountName, from);
                    continue;
                }

                double amount = r.path("amount").asDouble(0.0);
                if (amount <= 0) { log.info("[Bybit][DIAG] Depósito omitido ({}) → amount<=0", accountName); continue; }

                long ts = parseLongSafe(r.path("successAt").asText(null));
                if (ts <= 0) { log.info("[Bybit][DIAG] Depósito omitido ({}) → sin successAt", accountName); continue; }
                LocalDate fecha = Instant.ofEpochMilli(ts).atZone(zona).toLocalDate();
                if (fecha.isBefore(desde)) {
                    log.info("[Bybit][DIAG] Depósito omitido ({}) → anterior a {} (fecha={}, txID={})", accountName, desde, fecha, txId);
                    continue;
                }
                log.info("[Bybit][DIAG] Depósito ACEPTADO ({}) → {} {} txID={}", accountName, amount, r.path("coin").asText("USDT"), txId);

                BuyDollarsDto dto = new BuyDollarsDto();
                dto.setAmount(amount);
                dto.setCryptoSymbol(r.path("coin").asText("USDT"));
                dto.setTasa(0.0);
                dto.setNameAccount(accountName);
                dto.setIdDeposit(txId);
                dto.setTxId(txId);
                dto.setPesos(0.0);
                dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), zona));
                dto.setAsignada(false);
                dto.setContraparteAddress(from);
                out.add(dto);
            }
        } catch (Exception e) {
            log.warn("[Bybit] No se pudieron leer depósitos entrantes de {}: {}", accountName, e.getMessage());
        }
        return out;
    }

    /**
     * Retiros de HOY de esta cuenta Bybit (on-chain, status=success), en el mismo formato
     * genérico que usa TronScan para "registrar ventas automáticamente".
     * Excluye retiros hacia una de NUESTRAS propias wallets registradas (traspaso interno).
     * Defensivo: nunca lanza, nunca null.
     */
    public List<SellDollarsDto> getOutgoingWithdrawals(String apiKey, String apiSecret, String accountName,
            Set<String> assignedIds, Set<String> ownAddresses) {
        return getOutgoingWithdrawals(apiKey, apiSecret, accountName, assignedIds, ownAddresses,
                LocalDate.now(ZoneId.of("America/Bogota")));
    }

    /** Igual que arriba pero permitiendo elegir la fecha DESDE (inclusive) — para importaciones
     *  manuales de días anteriores (prueba/reproceso). El automático usa siempre HOY. */
    public List<SellDollarsDto> getOutgoingWithdrawals(String apiKey, String apiSecret, String accountName,
            Set<String> assignedIds, Set<String> ownAddresses, LocalDate desde) {
        List<SellDollarsDto> out = new ArrayList<>();
        if (apiKey == null || apiSecret == null || apiKey.isBlank() || apiSecret.isBlank()) {
            log.warn("[Bybit][DIAG] Retiros {}: cuenta sin apiKey/apiSecret → no se consulta.", accountName);
            return out;
        }

        Set<String> propias = normalizarTodas(ownAddresses);
        ZoneId zona = ZoneId.of("America/Bogota");

        try {
            long end = System.currentTimeMillis();
            long start = desde.atStartOfDay(zona).toInstant().toEpochMilli();
            String query = "startTime=" + start + "&endTime=" + end + "&limit=50";
            JsonNode root = signedGet("/v5/asset/withdraw/query-record", query, apiKey, apiSecret);

            JsonNode rows = root.path("result").path("rows");
            log.info("[Bybit][DIAG] Retiros {}: Bybit devolvió {} fila(s) en la ventana de hoy.",
                    accountName, rows.size());
            for (JsonNode r : rows) {
                String status = r.path("status").asText("");
                if (!"success".equalsIgnoreCase(status)) { log.info("[Bybit][DIAG] Retiro omitido ({}) → status={} (no success)", accountName, status); continue; }
                String txId = r.path("txID").asText(null);
                if (txId == null || txId.isBlank()) { log.info("[Bybit][DIAG] Retiro omitido ({}) → sin txID", accountName); continue; }
                if (assignedIds != null && assignedIds.contains(txId)) { log.info("[Bybit][DIAG] Retiro omitido ({}) → ya registrado txID={}", accountName, txId); continue; }

                String to = r.path("toAddress").asText(null);
                if (to != null && !to.isBlank() && propias.contains(normalizar(to))) {
                    log.info("[Bybit][DIAG] Retiro omitido ({}) → va a wallet propia (traspaso) to={}", accountName, to);
                    continue;
                }

                double amount = r.path("amount").asDouble(0.0);
                if (amount <= 0) { log.info("[Bybit][DIAG] Retiro omitido ({}) → amount<=0", accountName); continue; }

                long ts = parseLongSafe(r.path("updateTime").asText(null));
                if (ts <= 0) ts = parseLongSafe(r.path("createTime").asText(null));
                if (ts <= 0) { log.info("[Bybit][DIAG] Retiro omitido ({}) → sin fecha", accountName); continue; }
                LocalDate fecha = Instant.ofEpochMilli(ts).atZone(zona).toLocalDate();
                if (fecha.isBefore(desde)) {
                    log.info("[Bybit][DIAG] Retiro omitido ({}) → anterior a {} (fecha={}, txID={})", accountName, desde, fecha, txId);
                    continue;
                }
                log.info("[Bybit][DIAG] Retiro ACEPTADO ({}) → {} {} txID={}", accountName, amount, r.path("coin").asText("USDT"), txId);

                SellDollarsDto dto = new SellDollarsDto();
                dto.setIdWithdrawals(txId);
                dto.setTxId(txId);
                dto.setNameAccount(accountName);
                dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), zona));
                dto.setDollars(amount);
                dto.setCryptoSymbol(r.path("coin").asText("USDT"));
                dto.setTasa(0.0);
                dto.setPesos(0.0);
                dto.setComision(0.0);
                dto.setContraparteAddress(to);
                out.add(dto);
            }
        } catch (Exception e) {
            log.warn("[Bybit] No se pudieron leer retiros salientes de {}: {}", accountName, e.getMessage());
        }
        return out;
    }

    private long parseLongSafe(String s) {
        if (s == null || s.isBlank()) return 0L;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0L; }
    }

    private Set<String> normalizarTodas(Set<String> addresses) {
        Set<String> out = new HashSet<>();
        if (addresses != null) for (String a : addresses) if (a != null) out.add(normalizar(a));
        return out;
    }

    /** Normaliza direcciones para comparar entre formatos (igual criterio que TraspasoWalletService). */
    private String normalizar(String w) {
        if (w == null) return "";
        String s = w.trim().toLowerCase();
        if (s.startsWith("0x")) s = s.substring(2);
        if (s.length() == 42 && s.startsWith("41")) s = s.substring(2);
        return s;
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
