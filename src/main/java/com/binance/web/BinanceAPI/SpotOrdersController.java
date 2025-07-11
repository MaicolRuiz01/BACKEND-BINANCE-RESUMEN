
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
import java.util.List;
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

import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.Transacciones;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.binance.web.OrderP2P.*;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SaleP2PRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.SellDollars.SellDollarsDto;
import com.binance.web.transacciones.TransaccionesDTO;
import com.binance.web.transacciones.TransaccionesRepository;

@RestController
@RequestMapping("/api/spot-orders")
@RequiredArgsConstructor 
public class SpotOrdersController {

	private final BinanceService            binanceService;
    private final SellDollarsRepository    sellDollarsRepository;
    private final BuyDollarsRepository     buyDollarsRepository;
    private final OrderP2PService          orderP2PService;
    private final SaleP2PRepository        saleP2PRepository;
    private final AccountBinanceRepository accountBinanceRepository;
    private final TransaccionesRepository transaccionesRepository;

    // ----------------- P2P Orders (filtradas) -----------------
	/*
	 * @GetMapping public ResponseEntity<List<OrderP2PDto>>
	 * getP2POrders(@RequestParam String account) { List<OrderP2PDto> ordenes =
	 * orderP2PService.showOrderP2PToday(account);
	 * 
	 * Set<String> asignadas = saleP2PRepository.findAll().stream()
	 * .map(SaleP2P::getNumberOrder) .collect(Collectors.toSet());
	 * 
	 * List<OrderP2PDto> sinAsignar = ordenes.stream() .filter(o ->
	 * !asignadas.contains(o.getOrderNumber())) .collect(Collectors.toList());
	 * 
	 * return ResponseEntity.ok(sinAsignar); }
	 */

    // Obtener depósitos en la billetera Spot, excluyendo:
    //  • Los ya convertidos en BuyDollars (idDeposit)
    //  • Las transacciones cuyo address esté registrado en AccountBinance.address
    @GetMapping("/depositos")
    public ResponseEntity<String> getSpotDeposits(
            @RequestParam String account,
            @RequestParam(defaultValue = "50") int limit) {

        String response = binanceService.getSpotDeposits(account, limit);

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root     = mapper.readTree(response);
            JsonNode dataNode = root.path("data");
            if (!dataNode.isArray()) {
                return ResponseEntity.ok(response);
            }
            ArrayNode sourceArray = (ArrayNode) dataNode;

            // 1) IDs ya usados en compras
            Set<String> assignedIds = buyDollarsRepository.findAll().stream()
                .map(bd -> bd.getIdDeposit())
                .collect(Collectors.toSet());

            // 2) Direcciones registradas que queremos excluir
            Set<String> blockedAddresses = getRegisteredAddresses();

            // 3) Filtramos
            ArrayNode filtered = mapper.createArrayNode();
            for (JsonNode deposit : sourceArray) {
                String id      = deposit.path("id").asText();
                String address = deposit.path("address").asText(null);
                if (!assignedIds.contains(id)
                        && (address == null || !blockedAddresses.contains(address))) {
                    filtered.add(deposit);
                }
            }

            // 4) Reemplazamos "data" y devolvemos
            ((ObjectNode) root).set("data", filtered);
            return ResponseEntity.ok(mapper.writeValueAsString(root));

        } catch (Exception e) {
            return ResponseEntity.ok(response);
        }
    }

	/*
	 * @GetMapping("/spot-balance") public ResponseEntity<String> getSpotBalance(
	 * 
	 * @RequestParam String account,
	 * 
	 * @RequestParam String asset) { return
	 * ResponseEntity.ok(binanceService.getSpotBalanceByAsset(account, asset)); }
	 */
	    
	    private Set<String> getRegisteredAddresses() {
	        return accountBinanceRepository.findAll().stream()
	            .map(AccountBinance::getAddress)
	            .filter(Objects::nonNull)
	            .collect(Collectors.toSet());
	    }
	    
	    
	    
	    
	    
	    
	    private boolean esCoincidenteConTransaccion(BuyDollarsDto dto, List<Transacciones> txsHoy) {
	        LocalDateTime fechaDto = dto.getDate();
	        return txsHoy.stream().anyMatch(tx -> {
	            if (Double.compare(tx.getCantidad(), dto.getDollars()) != 0) return false;
	            long diffSeconds = Math.abs(Duration.between(tx.getFecha(), fechaDto).toSeconds());
	            return diffSeconds <= 60;
	        });
	    }

	    
	    
	    
	    
	    
	    
	    
	    private List<BuyDollarsDto> obtenerComprasNoRegistradas(int limit) throws Exception {
	        ObjectMapper mapper = new ObjectMapper();
	        List<BuyDollarsDto> resultado = new ArrayList<>();

	        // 1) IDs ya usados en compras (solo se carga una vez)
	        Set<String> assignedIds = buyDollarsRepository.findAll().stream()
	                .map(BuyDollars::getIdDeposit)
	                .collect(Collectors.toSet());

	        // 2) Transacciones de hoy (o cambia tu rango si lo necesitas)
	        LocalDateTime inicio = LocalDate.now().atStartOfDay();
	        LocalDateTime fin    = LocalDate.now().atTime(LocalTime.MAX);
	        List<Transacciones> txsHoy = transaccionesRepository.findByFechaBetween(inicio, fin);

	        // 3) Trae todos los depósitos Spot
	        for (String account : binanceService.getAllAccountNames()) {
	            String response = binanceService.getSpotDeposits(account, limit);
	            JsonNode root = mapper.readTree(response);

	            ArrayNode sourceArray;
	            if (root.isArray()) {
	                sourceArray = (ArrayNode) root;
	            } else {
	                JsonNode dataNode = root.path("data");
	                if (!dataNode.isArray()) continue;
	                sourceArray = (ArrayNode) dataNode;
	            }

	            for (JsonNode deposit : sourceArray) {
	                String id          = deposit.path("id").asText();
	                double amount      = deposit.path("amount").asDouble(0);
	                String tsStr       = deposit.path("insertTime").asText(null);

	                // 4) Filtros básicos
	                if (assignedIds.contains(id) || amount <= 0) continue;

	                // 5) Parsear fecha
	                LocalDateTime fecha = null;
	                if (tsStr != null) {
	                    try {
	                        long ts = Long.parseLong(tsStr);
	                        fecha = parseFechaDesdeTimestamp(ts);
	                    } catch (NumberFormatException ex) {
	                        fecha = parseFechaDesdeString(tsStr);
	                    }
	                }

	                // 6) Construir DTO
	                BuyDollarsDto compra = new BuyDollarsDto();
	                compra.setIdDeposit(id);
	                compra.setNameAccount(account);
	                compra.setDate(fecha);
	                compra.setDollars(amount);
	                resultado.add(compra);
	            }
	        }

	        // 7) Filtrar los que coinciden (monto + fecha ±60s) con txsHoy
	        return resultado.stream()
	                .filter(dto -> {
	                    LocalDateTime dtoFecha = dto.getDate();
	                    return txsHoy.stream().noneMatch(tx -> {
	                        // comparar monto
	                        if (Double.compare(tx.getCantidad(), dto.getDollars()) != 0) {
	                            return false;
	                        }
	                        // comparar fecha dentro de ±60s
	                        long diffSec = Math.abs(Duration.between(tx.getFecha(), dtoFecha).toSeconds());
	                        return diffSec <= 60;
	                    });
	                })
	                .collect(Collectors.toList());
	    }

	    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	    private LocalDateTime parseFechaDesdeString(String fechaStr) {
	        try {
	            return LocalDateTime.parse(fechaStr, FORMATO_FECHA);
	        } catch (DateTimeParseException e) {
	            System.err.println("Error parseando fecha: " + fechaStr);
	            return null;
	        }
	    }

	    private class ClasificacionMovimientos {
	        List<SellDollarsDto> ventas = new ArrayList<>();
	        List<TransaccionesDTO> traspasos = new ArrayList<>();
	    }

	    private ClasificacionMovimientos obtenerMovimientosClasificados(int limit) throws Exception {
	        ObjectMapper mapper = new ObjectMapper();
	        ClasificacionMovimientos resultado = new ClasificacionMovimientos();

	        Set<String> registeredAddresses = getRegisteredAddresses();
	        Set<String> assignedIds = transaccionesRepository.findAll().stream()
	                .map(t -> t.getIdtransaccion())
	                .collect(Collectors.toSet());
	        Set<String> ventaIds = sellDollarsRepository.findAll().stream()
	                .map(s -> s.getIdWithdrawals())
	                .collect(Collectors.toSet());


	        for (String account : binanceService.getAllAccountNames()) {
	            String response = binanceService.getSpotWithdrawals(account, limit);
	            JsonNode root = mapper.readTree(response);

	            ArrayNode sourceArray;
	            if (root.isArray()) {
	                sourceArray = (ArrayNode) root;
	            } else {
	                JsonNode dataNode = root.path("data");
	                if (!dataNode.isArray()) {
	                    continue;
	                }
	                sourceArray = (ArrayNode) dataNode;
	            }

	            for (JsonNode withdrawal : sourceArray) {
	                String id = withdrawal.path("id").asText();
	                String address = withdrawal.path("address").asText(null);
	                String coin = withdrawal.path("coin").asText("");
	                String timestampStr = withdrawal.path("applyTime").asText(null);

	                if (assignedIds.contains(id)|| ventaIds.contains(id)) continue;

	                LocalDateTime fecha = null;
	                if (timestampStr != null) {
	                    try {
	                        long ts = Long.parseLong(timestampStr);
	                        fecha = parseFechaDesdeTimestamp(ts);
	                    } catch (NumberFormatException e) {
	                        fecha = parseFechaDesdeString(timestampStr);
	                    }
	                }

	                if (address != null && registeredAddresses.contains(address)) {
	                    TransaccionesDTO traspaso = new TransaccionesDTO();
	                    traspaso.setIdtransaccion(id);
	                    traspaso.setCuentaFrom(account);
	                    traspaso.setCuentaTo(address);
	                    traspaso.setFecha(fecha);

	                    if (coin.equalsIgnoreCase("TRX") && fecha != null) {
	                        Double tasaTRXUSDT = binanceService.getHistoricalPriceTRXUSDT(fecha);
	                        traspaso.setMonto(withdrawal.path("amount").asDouble(0) * (tasaTRXUSDT != null ? tasaTRXUSDT : 1));
	                        traspaso.setTipo(String.format("TRX (tasa: %s USDT)", tasaTRXUSDT != null ? tasaTRXUSDT : "N/A"));
	                    } else {
	                        traspaso.setMonto(withdrawal.path("amount").asDouble(0));
	                        traspaso.setTipo(coin);
	                    }
	                    resultado.traspasos.add(traspaso);

	                } else {
	                    SellDollarsDto venta = new SellDollarsDto();
	                    venta.setIdWithdrawals(id);
	                    venta.setNameAccount(account);
	                    venta.setDate(fecha);

	                    if (coin.equalsIgnoreCase("TRX") && fecha != null) {
	                        Double tasaTRXUSDT = binanceService.getHistoricalPriceTRXUSDT(fecha);
	                        venta.setEquivalenteciaTRX(withdrawal.path("amount").asDouble(0));
	                        venta.setDollars(withdrawal.path("amount").asDouble(0) * (tasaTRXUSDT != null ? tasaTRXUSDT : 1));
	                    } else {
	                        venta.setDollars(withdrawal.path("amount").asDouble(0));
	                        venta.setEquivalenteciaTRX(null);
	                    }

	                    resultado.ventas.add(venta);
	                }
	            }
	        }
	        return resultado;
	    }


	    private LocalDateTime parseFechaDesdeTimestamp(long timestamp) {
	        if (timestamp > 1_000_000_000_000_000L) {
	            timestamp = timestamp / 1000; // microsegundos a milisegundos
	        }
	        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota"));
	    }










	    @GetMapping("/ventas-no-registradas")
	    public ResponseEntity<?> getVentasNoRegistradas(@RequestParam(defaultValue = "100") int limit) {
	        try {
	            ClasificacionMovimientos movimientos = obtenerMovimientosClasificados(limit);
	            return ResponseEntity.ok(movimientos.ventas);
	        } catch (Exception e) {
	            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body("{\"error\":\"" + e.getMessage() + "\"}");
	        }
	    }


	    @GetMapping("/traspasos-no-registrados")
	    public ResponseEntity<List<TransaccionesDTO>> getTraspasosNoRegistrados(
	            @RequestParam(defaultValue = "100") int limit) {
	        try {
	            ClasificacionMovimientos movimientos = obtenerMovimientosClasificados(limit);
	            return ResponseEntity.ok(movimientos.traspasos);
	        } catch (Exception e) {
	            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                    .body(new ArrayList<>());
	        }
	    }
	    
	    @GetMapping("/compras-no-registradas")
	    public ResponseEntity<List<BuyDollarsDto>> getComprasNoRegistradas(
	            @RequestParam(defaultValue = "100") int limit) {
	        try {
	            List<BuyDollarsDto> compras = obtenerComprasNoRegistradas(limit);
	            return ResponseEntity.ok(compras);
	        } catch (Exception e) {
	            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                    .body(Collections.emptyList());
	        }
	    }



}