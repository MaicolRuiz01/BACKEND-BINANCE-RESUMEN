
package com.binance.web.BinanceAPI;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.RequiredArgsConstructor;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.Transacciones;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.binance.web.OrderP2P.*;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SaleP2PRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.model.BuyDollarsDto;
import com.binance.web.model.SellDollarsDto;
import com.binance.web.transacciones.TransaccionesDTO;
import com.binance.web.transacciones.TransaccionesRepository;
import com.binance.web.util.TraspasoWalletService;
@RestController
@RequestMapping("/api/spot-orders")
@RequiredArgsConstructor
public class SpotOrdersController {

    private final BinanceService binanceService;
    private final SellDollarsRepository sellDollarsRepository;
    private final BuyDollarsRepository buyDollarsRepository;
    private final AccountBinanceRepository accountBinanceRepository;
    private final TransaccionesRepository transaccionesRepository;
    private final TronScanService tronScanService;
    private final TraspasoWalletService traspasoWalletService;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Devuelve depósitos en la billetera Spot que no han sido registrados como compras
     * y no provienen de direcciones internas.
     */
    @GetMapping("/depositos")
    public ResponseEntity<String> getSpotDeposits(@RequestParam String account, @RequestParam(defaultValue = "50") int limit) {
        String response = binanceService.getSpotDeposits(account, limit);
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            JsonNode dataNode = root.path("data");
            if (!dataNode.isArray()) {
                return ResponseEntity.ok(response);
            }
            ArrayNode sourceArray = (ArrayNode) dataNode;

            Set<String> assignedIds = buyDollarsRepository.findAll().stream()
                    .map(BuyDollars::getIdDeposit)
                    .collect(Collectors.toSet());
            Set<String> blockedAddresses = getRegisteredAddresses();

            ArrayNode filtered = mapper.createArrayNode();

         // 🔥 NUEVO: obtengo mis direcciones internas normalizadas
         Set<String> ownAddrs = getRegisteredAddresses().stream()
                 .map(a -> a.trim().toLowerCase())
                 .collect(Collectors.toSet());

         for (JsonNode deposit : sourceArray) {

             String id       = deposit.path("id").asText();
             String address  = deposit.path("address").asText(null);
             String txId     = deposit.path("txId").asText(null);
             String network  = deposit.path("network").asText(null);
             String coin     = deposit.path("coin").asText("");

             // 🔥 Detectar si es traspaso interno por blockchain (txId)
             boolean internal = isInternalDeposit(txId, coin, network, ownAddrs);

             // ❌ Ya registrada como compra
             if (assignedIds.contains(id)) continue;

             // ❌ Es dirección interna explícita
             if (address != null && ownAddrs.contains(address.trim().toLowerCase())) continue;

             // ❌ Detectado interno por txId
             if (internal) continue;

             filtered.add(deposit);
         }

            ((ObjectNode) root).set("data", filtered);
            return ResponseEntity.ok(mapper.writeValueAsString(root));
        } catch (Exception e) {
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Devuelve retiros de la billetera Spot, excluyendo los ya registrados como ventas
     * o que son traspasos internos.
     */
    @GetMapping("/retiros")
    public ResponseEntity<String> getSpotWithdrawals(@RequestParam String account, @RequestParam(defaultValue = "50") int limit) {
        String response = binanceService.getSpotWithdrawals(account, limit);
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);
            JsonNode dataNode = root.path("data");
            if (!dataNode.isArray()) {
                return ResponseEntity.ok(response);
            }
            ArrayNode sourceArray = (ArrayNode) dataNode;

            Set<String> assignedIds = sellDollarsRepository.findAll().stream()
                    .map(SellDollars::getIdWithdrawals)
                    .collect(Collectors.toSet());
            Set<String> internalAddresses = getRegisteredAddresses();

            ArrayNode filtered = mapper.createArrayNode();
            for (JsonNode withdrawal : sourceArray) {
                String id = withdrawal.path("id").asText();
                String address = withdrawal.path("address").asText(null);
                if (!assignedIds.contains(id) && (address == null || !internalAddresses.contains(address))) {
                    filtered.add(withdrawal);
                }
            }
            ((ObjectNode) root).set("data", filtered);
            return ResponseEntity.ok(mapper.writeValueAsString(root));
        } catch (Exception e) {
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Devuelve depósitos de hoy que no han sido registrados como compras.
     */
    @GetMapping("/compras-no-registradas")
    public ResponseEntity<List<BuyDollarsDto>> getComprasNoRegistradas(
            @RequestParam(defaultValue = "30") int limit) {

        List<BuyDollarsDto> resultado = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        ZoneId zoneId = ZoneId.of("America/Bogota");
        LocalDate hoy = LocalDate.now(zoneId);

        try {
            // 1) IDs de depósitos ya registrados como BuyDollars
            Set<String> comprasIds = buyDollarsRepository.findAll().stream()
                    .map(BuyDollars::getIdDeposit)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // 2) Mis direcciones internas (normalizadas)
            Set<String> ownAddrsLower = accountBinanceRepository.findAll().stream()
                    .map(AccountBinance::getAddress)
                    .filter(Objects::nonNull)
                    .map(a -> a.trim().toLowerCase())
                    .collect(Collectors.toSet());

            // 3) txId de traspasos internos detectados por withdrawals hacia direcciones internas
            Set<String> internalTxIds = new HashSet<>();

            for (String account : binanceService.getAllAccountNames()) {
                String wresp = binanceService.getSpotWithdrawals(account, limit);
                JsonNode wroot = mapper.readTree(wresp);

                ArrayNode warr;
                if (wroot.isArray()) {
                    warr = (ArrayNode) wroot;
                } else {
                    JsonNode dataNode = wroot.path("data");
                    if (!dataNode.isArray()) continue;
                    warr = (ArrayNode) dataNode;
                }

                for (JsonNode wd : warr) {
                    String addr = wd.path("address").asText(null);
                    String txId = wd.path("txId").asText(null);

                    // retiro a address interna => traspaso interno
                    if (addr != null && ownAddrsLower.contains(addr.trim().toLowerCase())) {
                        if (txId != null && !txId.isBlank()) {
                            internalTxIds.add(txId.trim());
                        }
                    }
                }
            }

            // 4) Ahora sí: recorremos depósitos y filtramos los internos por txId
            for (String account : binanceService.getAllAccountNames()) {
                String resp = binanceService.getSpotDeposits(account, limit);
                JsonNode root = mapper.readTree(resp);

                ArrayNode arr;
                if (root.isArray()) {
                    arr = (ArrayNode) root;
                } else {
                    JsonNode dataNode = root.path("data");
                    if (!dataNode.isArray()) continue;
                    arr = (ArrayNode) dataNode;
                }

                for (JsonNode dep : arr) {
                    String id = dep.path("id").asText();
                    double amount = dep.path("amount").asDouble(0);
                    String coin = dep.path("coin").asText("");
                    String txId = dep.path("txId").asText(null);
                    String network = dep.path("network").asText(null);
                    String tsStr = dep.path("insertTime").asText(null);

                    // ya registrado o inválido
                    if (comprasIds.contains(id) || amount <= 0) continue;

                    // ✅ filtro: si es el "mismo txId" de un retiro interno, NO es compra
                    if (txId != null && internalTxIds.contains(txId.trim())) continue;

                    LocalDateTime fecha = parseFecha(tsStr);
                    if (fecha != null && fecha.toLocalDate().isEqual(hoy)) {
                        // El depósito de Binance NO trae el remitente; se resuelve on-chain por txId.
                        String remitente = resolverRemitenteOnChain(txId, network);

                        // Si el remitente es UNA DE NUESTRAS wallets (ej. la wallet TRON que solo
                        // reenvía a Binance el dinero que vino de Bybit), es un traspaso INTERNO,
                        // NO una compra → se omite. Así deja de contarse como compra.
                        if (remitente != null && ownAddrsLower.contains(remitente.trim().toLowerCase())) {
                            continue;
                        }

                        BuyDollarsDto dto = new BuyDollarsDto();
                        dto.setIdDeposit(id);
                        // Guardar el HASH on-chain del depósito. Es clave para detectar traspasos:
                        // este mismo hash aparece en el RETIRO de la cuenta Bybit que lo envió, y así
                        // se cruzan (antes se descartaba y por eso nunca matcheaba → salía "Externa").
                        dto.setTxId(txId);
                        dto.setNameAccount(account);
                        dto.setDate(fecha);
                        dto.setAmount(amount);
                        dto.setCryptoSymbol(coin);
                        // Bybit → el servicio lo tratará como traspaso; otro externo → compra.
                        dto.setContraparteAddress(remitente);
                        resultado.add(dto);
                    }
                }
            }

            return ResponseEntity.ok(resultado);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }

    
    /**
     * Retorna retiros (ventas) de hoy que no son traspasos internos.
     */
    @GetMapping("/ventas-no-registradas")
    public ResponseEntity<List<SellDollarsDto>> getVentasNoRegistradas(@RequestParam(defaultValue = "50") int limit) {
        try {
            Set<String> ventasIds = sellDollarsRepository.findAll().stream()
                .map(SellDollars::getIdWithdrawals)
                .collect(Collectors.toSet());
            Set<String> internalAddrs = getRegisteredAddresses();
            
            List<SellDollarsDto> result = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();
            LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
            
            for (String account : binanceService.getAllAccountNames()) {
                String resp = binanceService.getSpotWithdrawals(account, limit);
                JsonNode root = mapper.readTree(resp);
                ArrayNode arr;
                
                if (root.isArray()) {
                    arr = (ArrayNode) root;
                } else {
                    JsonNode dataNode = root.path("data");
                    if (!dataNode.isArray()) continue;
                    arr = (ArrayNode) dataNode;
                }
                
                for (JsonNode wd : arr) {
                    String id = wd.path("id").asText();
                    String addr = wd.path("address").asText(null);
                    String coin = wd.path("coin").asText(""); // 🔥 cualquier cripto
                    String tsStr = wd.path("applyTime").asText(null);
                    double amount = wd.path("amount").asDouble(0);
                    
                    // Omitir si ya registrada o es traspaso interno
                    if (ventasIds.contains(id) || (addr != null && internalAddrs.contains(addr))) {
                        continue;
                    }
                    
                    LocalDateTime fecha = parseFecha(tsStr);
                    if (fecha != null && fecha.toLocalDate().isEqual(hoy)) {
                        SellDollarsDto dto = new SellDollarsDto();
                        dto.setIdWithdrawals(id);
                        dto.setNameAccount(account);
                        dto.setDate(fecha);
                        dto.setDollars(amount);         // cantidad retirada en cripto
                        dto.setCryptoSymbol(coin);      // moneda retirada
                        dto.setContraparteAddress(addr); // destino del retiro → detectar wallet Bybit (traspaso)
                        result.add(dto);
                    }
                }
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }
    
    /**
     * Retorna traspasos de hoy detectados a partir de retiros hacia cuentas internas.
     */
    @GetMapping("/traspasos-no-registrados")
    public ResponseEntity<List<TransaccionesDTO>> getTraspasosNoRegistrados(
            @RequestParam(defaultValue = "20") int limit) {
        List<TransaccionesDTO> result = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
        
        try {
        	Set<String> txIdsRegistrados = transaccionesRepository.findAll().stream()
        		    .map(Transacciones::getTxId)
        		    .filter(Objects::nonNull)
        		    .collect(Collectors.toSet());
            Set<String> internalAddrs = getRegisteredAddresses();
            
            for (String account : binanceService.getAllAccountNames()) {
                String resp = binanceService.getSpotWithdrawals(account, limit);
                JsonNode root = mapper.readTree(resp);
                ArrayNode arr;
                if (root.isArray()) {
                    arr = (ArrayNode) root;
                } else {
                    JsonNode dataNode = root.path("data");
                    if (!dataNode.isArray()) continue;
                    arr = (ArrayNode) dataNode;
                }
                
                for (JsonNode wd : arr) {
                    String id = wd.path("id").asText();
                    String addr = wd.path("address").asText(null);
                    String coin = wd.path("coin").asText(""); // 🔥 cualquier cripto
                    double amount = wd.path("amount").asDouble(0);
                    String tsStr = wd.path("applyTime").asText(null);
                    String txId = wd.path("txId").asText(null);

                    // Si ya registrado o no es interno → se omite
                    if ((txId != null && txIdsRegistrados.contains(txId)) ||
                    	    (addr == null || !internalAddrs.contains(addr))) {
                    	    continue;
                    	}
                    
                    LocalDateTime fecha = parseFecha(tsStr);
                    if (fecha != null && fecha.toLocalDate().isEqual(hoy)) {
                        TransaccionesDTO dto = new TransaccionesDTO();
                        dto.setIdtransaccion(id);
                        dto.setCuentaFrom(account);
                        dto.setCuentaTo(addr);
                        dto.setFecha(fecha);
                        dto.setMonto(amount);
                        dto.setTipo(coin); // 🔥 cripto real usada
                        dto.setTxId(txId);
                        result.add(dto);
                    }
                }
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }

    /**
     * ENDPOINT DE PRUEBA (no guarda nada en BD). Trae los depósitos y retiros de AYER de una
     * cuenta (por defecto Luis) y clasifica cada uno como TRASPASO / COMPRA / VENTA aplicando la
     * MISMA lógica del import real (remitente on-chain, wallet Bybit, direcciones propias).
     * Sirve para verificar la detección sin efectos secundarios.
     * GET /api/spot-orders/diagnostico-ayer?account=Luis
     */
    @GetMapping("/diagnostico-ayer")
    public ResponseEntity<Map<String, Object>> diagnosticoAyer(
            @RequestParam(defaultValue = "Luis") String account,
            @RequestParam(defaultValue = "100") int limit) {

        ZoneId zone = ZoneId.of("America/Bogota");
        LocalDate ayer = LocalDate.now(zone).minusDays(1);
        LocalDateTime inicio = ayer.atStartOfDay();
        LocalDateTime fin = ayer.plusDays(1).atStartOfDay();

        // Direcciones propias (normalizadas) para detectar traspasos internos.
        Set<String> ownAddrs = accountBinanceRepository.findAll().stream()
                .map(AccountBinance::getAddress)
                .filter(Objects::nonNull)
                .map(a -> a.trim().toLowerCase())
                .collect(Collectors.toSet());

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> movimientos = new ArrayList<>();

        // ===== DEPÓSITOS (entradas) =====
        try {
            JsonNode root = mapper.readTree(binanceService.getSpotDeposits(account, limit));
            JsonNode arr = root.isArray() ? root : root.path("data");
            if (arr.isArray()) {
                for (JsonNode dep : arr) {
                    LocalDateTime fecha = parseFecha(dep.path("insertTime").asText(null));
                    if (fecha == null || fecha.isBefore(inicio) || !fecha.isBefore(fin)) continue; // solo ayer

                    String txId = dep.path("txId").asText(null);
                    String network = dep.path("network").asText(null);
                    String remitente = resolverRemitenteOnChain(txId, network);

                    String tipo, razon;
                    if (remitente != null && traspasoWalletService.esWalletTraspaso(remitente)) {
                        tipo = "TRASPASO";
                        razon = "Depósito que VIENE de la wallet Bybit";
                    } else if (remitente != null && ownAddrs.contains(remitente.trim().toLowerCase())) {
                        tipo = "TRASPASO INTERNO";
                        razon = "Viene de una wallet nuestra";
                    } else {
                        tipo = "COMPRA";
                        razon = (remitente == null)
                                ? "No se pudo resolver el remitente on-chain → se tomaría como compra"
                                : "Remitente externo desconocido → compra";
                    }

                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("direccion", "ENTRADA (depósito)");
                    m.put("fecha", fecha.toString());
                    m.put("coin", dep.path("coin").asText(""));
                    m.put("monto", dep.path("amount").asDouble(0));
                    m.put("txId", txId);
                    m.put("remitente", remitente);
                    m.put("clasificacion", tipo);
                    m.put("razon", razon);
                    movimientos.add(m);
                }
            }
        } catch (Exception e) {
            movimientos.add(Map.<String, Object>of("error_depositos", String.valueOf(e.getMessage())));
        }

        // ===== RETIROS (salidas) =====
        try {
            JsonNode root = mapper.readTree(binanceService.getSpotWithdrawals(account, limit));
            JsonNode arr = root.isArray() ? root : root.path("data");
            if (arr.isArray()) {
                for (JsonNode wd : arr) {
                    LocalDateTime fecha = parseFecha(wd.path("applyTime").asText(null));
                    if (fecha == null || fecha.isBefore(inicio) || !fecha.isBefore(fin)) continue; // solo ayer

                    String destino = wd.path("address").asText(null);

                    String tipo, razon;
                    if (destino != null && traspasoWalletService.esWalletTraspaso(destino)) {
                        tipo = "TRASPASO";
                        razon = "Retiro que VA a la wallet Bybit";
                    } else if (destino != null && ownAddrs.contains(destino.trim().toLowerCase())) {
                        tipo = "TRASPASO INTERNO";
                        razon = "Va a una wallet nuestra";
                    } else {
                        tipo = "VENTA";
                        razon = "Destino externo desconocido → venta";
                    }

                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("direccion", "SALIDA (retiro)");
                    m.put("fecha", fecha.toString());
                    m.put("coin", wd.path("coin").asText(""));
                    m.put("monto", wd.path("amount").asDouble(0));
                    m.put("txId", wd.path("txId").asText(null));
                    m.put("destino", destino);
                    m.put("clasificacion", tipo);
                    m.put("razon", razon);
                    movimientos.add(m);
                }
            }
        } catch (Exception e) {
            movimientos.add(Map.<String, Object>of("error_retiros", String.valueOf(e.getMessage())));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cuenta", account);
        out.put("fecha", ayer.toString());
        out.put("nota", "Solo diagnóstico — NO se guardó nada en la BD.");
        out.put("total", movimientos.size());
        out.put("movimientos", movimientos);
        return ResponseEntity.ok(out);
    }

    /**
     * Helper para parsear fechas.
     */
    private LocalDateTime parseFecha(String dateStr) {
        if (dateStr == null) return null;
        try {
            long ts = Long.parseLong(dateStr);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.of("America/Bogota"));
        } catch (NumberFormatException ex) {
            try {
                return LocalDateTime.parse(dateStr, FORMATO_FECHA)
                        .atZone(ZoneId.of("UTC"))
                        .withZoneSameInstant(ZoneId.of("America/Bogota"))
                        .toLocalDateTime();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    /**
     * Obtiene direcciones registradas de cuentas Binance.
     */
    private Set<String> getRegisteredAddresses() {
        return accountBinanceRepository.findAll().stream()
                .map(AccountBinance::getAddress)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @GetMapping("/spot-trades")
    public ResponseEntity<String> getAllSpotTrades(@RequestParam(defaultValue = "TRXUSDT") String symbol,
            @RequestParam(defaultValue = "100") int limit) {
        String response = binanceService.getAllSpotTradesForAllAccounts(symbol, limit);
        return ResponseEntity.ok(response);
    }

    /**
     * Resuelve el remitente (from) on-chain de un depósito a partir de su txId.
     * El historial de depósitos de Binance NO trae quién envió, así que se consulta
     * la transacción TRON por hash. Se usa para detectar depósitos que vienen de la
     * wallet de traspaso (Bybit) y tratarlos como TRASPASO en vez de compra.
     * Devuelve la dirección base58 del emisor, o null si no se puede resolver / no es on-chain.
     */
    private String resolverRemitenteOnChain(String txId, String network) {
        if (txId == null || txId.isBlank() || txId.startsWith("Off-chain")) return null;
        try {
            boolean esTron = network == null
                    || "TRC20".equalsIgnoreCase(network)
                    || "TRX".equalsIgnoreCase(network)
                    || "TRON".equalsIgnoreCase(network);
            if (!esTron) return null;
            JsonNode tx = tronScanService.getTxByHash(txId);
            String from = tx.path("from").asText(null);
            return (from != null && !from.isBlank()) ? from : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isInternalDeposit(String txId, String coin, String network, Set<String> ownAddrs) {
        try {
            if (txId == null) return false;

            // ✅ CASO 1: Off-chain (Binance interno) -> NO buscar en TronScan
            if (txId.startsWith("Off-chain transfer")) {
                return true; // o mejor: return txIdsInternos.contains(txId) si lo pasas como parámetro
            }

            // ✅ CASO 2: On-chain real -> TronScan
            if ("TRC20".equalsIgnoreCase(network) || "TRX".equalsIgnoreCase(network)) {
                JsonNode tx = tronScanService.getTxByHash(txId);
                String fromAddr = tx.path("from").asText(null);
                if (fromAddr != null && ownAddrs.contains(fromAddr.trim().toLowerCase())) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    
    private Set<String> getOwnAddressesLower() {
    	  return accountBinanceRepository.findAll().stream()
    	    .map(AccountBinance::getAddress)
    	    .filter(Objects::nonNull)
    	    .map(a -> a.trim().toLowerCase())
    	    .collect(Collectors.toSet());
    	}

}