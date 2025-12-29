package com.binance.web.BinanceAPI;

import com.binance.web.Entity.Cliente;
import com.binance.web.model.BuyDollarsDto;
import com.binance.web.model.SellDollarsDto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;

@Service
public class TronScanService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Lee las keys desde application.properties (si no están, queda vacío y no se envía header).
    @Value("${tronscan.apiKey:}")
    private String tronscanApiKey;

    @Value("${trongrid.apiKey:}")
    private String tronGridApiKey;

    /* ===================== Helpers de headers ===================== */

    private HttpEntity<Void> tronScanEntity() {
        HttpHeaders h = new HttpHeaders();
        if (tronscanApiKey != null && !tronscanApiKey.isBlank()) {
            h.set("TRON-PRO-API-KEY", tronscanApiKey);
        }
        return new HttpEntity<>(h);
    }

    private HttpEntity<Void> tronGridEntity() {
        HttpHeaders h = new HttpHeaders();
        if (tronGridApiKey != null && !tronGridApiKey.isBlank()) {
            h.set("TRON-PRO-API-KEY", tronGridApiKey);
        }
        return new HttpEntity<>(h);
    }

    /* ===================== Endpoints RAW ===================== */

    /** Transacciones nativas TRX (entradas/salidas) via TronScan. */
    public String getTransactions(String address) {
        String url = "https://apilist.tronscanapi.com/api/transaction"
                + "?sort=-timestamp&count=true&limit=20&start=0&address=" + address;
        return restTemplate.exchange(url, HttpMethod.GET, tronScanEntity(), String.class).getBody();
    }

    public String getTRC20TransfersUsingTronGrid(String address) {
        String url = "https://api.trongrid.io/v1/accounts/" + address
                + "/transactions/trc20?limit=100&only_confirmed=true&order_by=block_timestamp,desc";
        return restTemplate.exchange(url, HttpMethod.GET, tronGridEntity(), String.class).getBody();
    }


    /** Detalle de transacción (para leer comisión/energy) via TronScan. */
    public JsonNode getTransactionDetailsFromTronScan(String txId) {
        String url = "https://apilist.tronscanapi.com/api/transaction-info?hash=" + txId;
        try {
            String response = restTemplate.exchange(url, HttpMethod.GET, tronScanEntity(), String.class).getBody();
            return objectMapper.readTree(response);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    /** Total assets en USD de una wallet TRUST via TronScan. */
    public double getTotalAssetTokenOverview(String walletAddress) {
        try {
            String url = "https://apilist.tronscanapi.com/api/account/token_asset_overview?address=" + walletAddress;
            String body = restTemplate.exchange(url, HttpMethod.GET, tronScanEntity(), String.class).getBody();
            JsonNode root = objectMapper.readTree(body);
            return root.path("totalAssetInUsd").asDouble(0.0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** Snapshot de balances por token (TRX + TRC20) via TronScan. */
    public Map<String, Double> getBalancesByAsset(String walletAddress) {
        Map<String, Double> out = new HashMap<>();
        try {
            String url = "https://apilist.tronscanapi.com/api/account?address=" + walletAddress;
            String resp = restTemplate.exchange(url, HttpMethod.GET, tronScanEntity(), String.class).getBody();
            JsonNode root = objectMapper.readTree(resp);

            // TRX (en SUN)
            double trxSun = root.path("balance").asDouble(0.0);
            out.put("TRX", trxSun / 1_000_000.0);

            // TRC20 tokens – puede venir en distintos campos
            JsonNode arr = root.path("trc20token_balances");
            if (!arr.isArray() || arr.size() == 0) arr = root.path("withPriceTokens");

            if (arr.isArray()) {
                for (JsonNode tok : arr) {
                    String symbol = tok.path("tokenAbbr").asText(tok.path("tokenName").asText("UNKNOWN")).toUpperCase();
                    int dec = tok.path("tokenDecimal").asInt(6);
                    String raw = tok.path("balance").asText(tok.path("quantity").asText("0"));
                    double qty = 0.0;
                    try { qty = Double.parseDouble(raw) / Math.pow(10, dec); } catch (NumberFormatException ignore) {}
                    if (qty > 0) out.merge(symbol, qty, Double::sum);
                }
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Error obteniendo snapshot TRUST: " + e.getMessage(), e);
        }
    }

    /* ===================== Parsers (tu lógica, sin tocar) ===================== */

    public List<BuyDollarsDto> parseIncomingTransactions(
            String jsonResponse,
            String walletAddress,
            Set<String> assignedIds) {

        List<BuyDollarsDto> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.path("data");

            if (data.isArray()) {
                for (JsonNode tx : data) {
                    String toAddress = tx.path("toAddress").asText();
                    String txId      = tx.path("hash").asText();
                    long timestamp   = tx.path("timestamp").asLong();
                    String amountStr = tx.path("amount").asText("0");

                    if (toAddress.equalsIgnoreCase(walletAddress) && !assignedIds.contains(txId)) {
                        double amountTRX = 0.0;
                        try { amountTRX = Double.parseDouble(amountStr) / 1_000_000.0; } catch (NumberFormatException ignore) {}
                        if (amountTRX <= 0) continue;

                        BuyDollarsDto dto = new BuyDollarsDto();
                        dto.setIdDeposit(txId);
                        dto.setNameAccount("TRUST");
                        dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota")));
                        dto.setAmount(amountTRX);
                        dto.setCryptoSymbol("TRX");
                        dto.setTasa(0.0);
                        dto.setPesos(0.0);
                        dto.setAsignada(false);
                        result.add(dto);
                    }
                }
            }
        } catch (Exception ignore) {}
        return result;
    }

    public List<BuyDollarsDto> parseOutgoingTransactions(
            String jsonResponse,
            String walletAddress,
            Set<String> assignedIds) {

        List<BuyDollarsDto> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.path("data");

            if (data.isArray()) {
                for (JsonNode tx : data) {
                    String fromAddress = tx.path("ownerAddress").asText();
                    String txId        = tx.path("hash").asText();
                    long timestamp     = tx.path("timestamp").asLong();
                    String amountStr   = tx.path("amount").asText("0");

                    if (fromAddress.equalsIgnoreCase(walletAddress) && !assignedIds.contains(txId)) {
                        double amountTRX = 0.0;
                        try { amountTRX = Double.parseDouble(amountStr) / 1_000_000.0; } catch (NumberFormatException ignore) {}
                        if (amountTRX <= 0) continue;

                        BuyDollarsDto dto = new BuyDollarsDto();
                        dto.setIdDeposit(txId);
                        dto.setNameAccount("TRUST_OUT");
                        dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota")));
                        dto.setAmount(amountTRX);
                        dto.setCryptoSymbol("TRX");
                        dto.setTasa(0.0);
                        dto.setPesos(0.0);
                        dto.setAsignada(false);
                        result.add(dto);
                    }
                }
            }
        } catch (Exception ignore) {}
        return result;
    }

    public List<SellDollarsDto> parseTRC20OutgoingTransfers(
            String jsonResponse,
            String walletAddress,
            String accountName,
            Set<String> assignedIds,
            Map<String, Cliente> clientePorWallet) {

        List<SellDollarsDto> result = new ArrayList<>();
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.path("data");
            if (!data.isArray()) return result;

            for (JsonNode tx : data) {
                // ----- CAMPOS DE TRONGRID -----
                String fromAddress = tx.path("from").asText(null);
                String toAddress   = tx.path("to").asText(null);
                String txId        = tx.path("transaction_id").asText(null);

                JsonNode tokenInfo = tx.path("token_info");
                String symbol      = tokenInfo.path("symbol").asText(null);

                // decimales pueden venir como int o string
                int decimals = 6;
                if (tokenInfo.has("decimals")) {
                    try { decimals = Integer.parseInt(tokenInfo.get("decimals").asText()); } catch (Exception ignore) {}
                }

                String rawValue = tx.path("value").asText("0");
                double amount = 0.0;
                try { amount = Double.parseDouble(rawValue) / Math.pow(10, decimals); } catch (NumberFormatException ignore) {}

                long ts = tx.path("block_timestamp").asLong(0L);

                // Filtro: solo salidas de esta wallet, no repetidas y de HOY
                if (fromAddress == null || txId == null || symbol == null) continue;
                if (!fromAddress.equalsIgnoreCase(walletAddress)) continue;
                if (assignedIds.contains(txId)) continue;

                LocalDate fechaTx = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota")).toLocalDate();
                if (!fechaTx.isEqual(hoy)) continue;

                // ----- Construcción del DTO -----
                SellDollarsDto dto = new SellDollarsDto();
                dto.setIdWithdrawals(txId);
                dto.setNameAccount(accountName);
                dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota")));
                dto.setDollars(amount);
                dto.setCryptoSymbol(symbol);
                dto.setTasa(0.0);
                dto.setPesos(0.0);

                // Comisión real en TRX (desde TronScan, usando el mismo txId)
                try {
                    JsonNode txDetails = getTransactionDetailsFromTronScan(txId);
                    double feeInSun = txDetails.path("cost").path("energy_fee").asDouble(0.0);
                    double feeTRX   = feeInSun / 1_000_000.0;
                    dto.setComision(feeTRX); // OJO: esto es TRX, no USDT
                } catch (Exception ignore) {
                    dto.setComision(0.0);
                }

                // asignar cliente si coincide la wallet destino
                if (toAddress != null && clientePorWallet != null) {
                    Cliente c = clientePorWallet.get(toAddress.trim().toLowerCase());
                    if (c != null) dto.setClienteId(c.getId());
                }

                result.add(dto);
            }
        } catch (Exception e) {
            // log opcional
            System.out.println("parseTRC20OutgoingTransfers error: " + e.getMessage());
        }

        return result;
    }


    public List<BuyDollarsDto> parseTRC20IncomingTransfers(
            String jsonResponse,
            String walletAddress,
            String accountName,
            Set<String> assignedIds) {

        List<BuyDollarsDto> result = new ArrayList<>();
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode tx : data) {
                    String toAddress = tx.path("to").asText();
                    String txId      = tx.path("transaction_id").asText();
                    JsonNode tokenInfo = tx.path("token_info");
                    String symbol    = tokenInfo.path("symbol").asText();
                    double amount    = Double.parseDouble(tx.path("value").asText("0")) / 1_000_000.0;

                    if (toAddress.equalsIgnoreCase(walletAddress) && !assignedIds.contains(txId)) {
                        long timestamp = tx.path("block_timestamp").asLong();
                        LocalDate fechaTx = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota")).toLocalDate();

                        if (fechaTx.isEqual(hoy)) {
                            BuyDollarsDto dto = new BuyDollarsDto();
                            dto.setAmount(amount);
                            dto.setCryptoSymbol(symbol);
                            dto.setTasa(0.0);
                            dto.setNameAccount(accountName);
                            dto.setIdDeposit(txId);
                            dto.setPesos(0.0);
                            dto.setDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota")));
                            dto.setAsignada(false);

                            result.add(dto);
                        }
                    }
                }
            }
        } catch (Exception ignore) {}
        return result;
    }

    /* ===================== Util opcional ===================== */

    /** Precio TRX/USDT aproximado en 1m candle que contiene el timestamp dado. */
    private double getTRXPriceAt(long timestamp) {
        try {
            long oneMinute = 60_000L;
            long startTime = (timestamp / oneMinute) * oneMinute;
            long endTime   = startTime + oneMinute;

            String url = String.format(
                    "https://api.binance.com/api/v3/klines?symbol=TRXUSDT&interval=1m&startTime=%d&endTime=%d",
                    startTime, endTime);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode array = objectMapper.readTree(response);
            if (array.isArray() && array.size() > 0) {
                return array.get(0).get(4).asDouble(); // close price
            }
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
    
 // == helpers Trongrid (sin tocar lo demás) ==
    public String getTrongridTrc20(String address, int limit) {
        String url = "https://api.trongrid.io/v1/accounts/" + address
                + "/transactions/trc20?limit=" + limit
                + "&only_confirmed=true&order_by=block_timestamp,desc";
        return restTemplate.exchange(url, HttpMethod.GET, tronGridEntity(), String.class).getBody();
    }
    public String getTrongridAccountTx(String address, int limit) {
        String url = "https://api.trongrid.io/v1/accounts/" + address
                + "/transactions?limit=" + limit
                + "&only_confirmed=true&order_by=block_timestamp,desc";
        return restTemplate.exchange(url, HttpMethod.GET, tronGridEntity(), String.class).getBody();
    }
    
    public String getUnifiedMovementsJson(String address, int limit) {
        ArrayNode out = objectMapper.createArrayNode();

        // TRC20
        try {
            String raw20 = getTrongridTrc20(address, limit);
            JsonNode data = objectMapper.readTree(raw20).path("data");
            if (data.isArray()) {
                for (JsonNode tx : data) {
                    String txId = tx.path("transaction_id").asText(null);
                    long ts     = tx.path("block_timestamp").asLong(0L);
                    String from = tx.path("from").asText((String) null);
                    String to   = tx.path("to").asText((String) null);

                    JsonNode token = tx.path("token_info");
                    String symbol  = token.path("symbol").asText("TOKEN");
                    int decimals   = 6;
                    if (token.has("decimals")) {
                        try { decimals = Integer.parseInt(token.get("decimals").asText()); } catch (Exception ignore) {}
                    }
                    double amount = 0.0;
                    try { amount = Double.parseDouble(tx.path("value").asText("0")) / Math.pow(10, decimals); } catch (Exception ignore) {}

                    if (txId != null && ts > 0) {
                        ObjectNode row = objectMapper.createObjectNode();
                        row.put("txId", txId);
                        row.put("timestamp", ts);
                        row.put("from", from);
                        row.put("to", to);
                        row.put("symbol", symbol);
                        row.put("amount", amount);
                        row.put("source", "TRC20");
                        out.add(row);
                    }
                }
            }
        } catch (Exception ignore) {}

        // TRX nativo
        try {
            String rawAcc = getTrongridAccountTx(address, limit);
            JsonNode data = objectMapper.readTree(rawAcc).path("data");
            if (data.isArray()) {
                for (JsonNode tx : data) {
                    String txId = tx.path("txID").asText(tx.path("transaction_id").asText(null));
                    long ts     = tx.path("block_timestamp").asLong(0L);

                    JsonNode contracts = tx.path("raw_data").path("contract");
                    if (contracts.isArray() && contracts.size() > 0) {
                        JsonNode c0 = contracts.get(0);
                        if ("TransferContract".equals(c0.path("type").asText())) {
                            JsonNode val = c0.path("parameter").path("value");
                            long sun   = val.path("amount").asLong(0L);
                            String from = val.path("owner_address").asText((String) null);
                            String to   = val.path("to_address").asText((String) null);
                            double trx  = sun / 1_000_000.0;

                            if (txId != null && ts > 0) {
                                ObjectNode row = objectMapper.createObjectNode();
                                row.put("txId", txId);
                                row.put("timestamp", ts);
                                row.put("from", from);
                                row.put("to", to);
                                row.put("symbol", "TRX");
                                row.put("amount", trx);
                                row.put("source", "TRX");
                                out.add(row);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignore) {}

        // ordenar (desc) por timestamp
        try {
            List<JsonNode> tmp = new ArrayList<>();
            out.forEach(tmp::add);
            tmp.sort((a,b) -> Long.compare(b.path("timestamp").asLong(0L), a.path("timestamp").asLong(0L)));
            ArrayNode sorted = objectMapper.createArrayNode();
            tmp.forEach(sorted::add);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sorted);
        } catch (Exception e) {
            return out.toString();
        }
    }
    public JsonNode getTxByHash(String txId) {
        // 1) TRC20: eventos del tx
        try {
            String urlEv = "https://api.trongrid.io/v1/transactions/" + txId + "/events";
            String body  = restTemplate.exchange(urlEv, HttpMethod.GET, tronGridEntity(), String.class).getBody();
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                for (JsonNode ev : data) {
                    String evName = ev.path("event_name").asText("");
                    if ("Transfer".equalsIgnoreCase(evName)) {
                        ObjectNode out = objectMapper.createObjectNode();
                        JsonNode res = ev.path("result");
                        out.put("from", res.path("from").asText((String) null));
                        out.put("to",   res.path("to").asText((String) null));
                        // si viene info del token, la agrego
                        String symbol = ev.path("token_info").path("symbol").asText((String) null);
                        if (symbol != null) out.put("symbol", symbol);
                        return out;
                    }
                }
            }
        } catch (Exception ignore) {}

        // 2) TRX nativo o contratos sin eventos Transfer
        try {
            String urlTx = "https://api.trongrid.io/v1/transactions/" + txId;
            String body  = restTemplate.exchange(urlTx, HttpMethod.GET, tronGridEntity(), String.class).getBody();
            JsonNode data = objectMapper.readTree(body).path("data");
            if (data.isArray() && data.size() > 0) {
                JsonNode tx = data.get(0);
                JsonNode contracts = tx.path("raw_data").path("contract");
                if (contracts.isArray() && contracts.size() > 0) {
                    JsonNode c0  = contracts.get(0);
                    JsonNode val = c0.path("parameter").path("value");
                    String from = val.path("owner_address").asText((String) null);
                    String to   = val.path("to_address").asText((String) null);

                    ObjectNode out = objectMapper.createObjectNode();
                    if (from != null) out.put("from", from);
                    if (to   != null) out.put("to",   to);
                    out.put("symbol", "TRX");
                    return out;
                }
            }
        } catch (Exception ignore) {}

        // 3) Fallback: TronScan (tu método ya existente)
        try {
            JsonNode scan = getTransactionDetailsFromTronScan(txId);
            // TronScan puede exponer varios alias para las direcciones
            String from = firstNonNull(
                    scan.path("transferFromAddress").asText(null),
                    scan.path("ownerAddress").asText(null),
                    scan.path("fromAddress").asText(null)
            );
            String to = firstNonNull(
                    scan.path("transferToAddress").asText(null),
                    scan.path("toAddress").asText(null)
            );

            ObjectNode out = objectMapper.createObjectNode();
            if (from != null) out.put("from", from);
            if (to   != null) out.put("to",   to);
            // symbol no siempre viene; lo omito si no está
            return out;
        } catch (Exception ignore) {}

        // Si nada funcionó, devuelvo objeto vacío
        return objectMapper.createObjectNode();
    }

    // helper pequeño para elegir el primer no-nulo
    private static String firstNonNull(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
