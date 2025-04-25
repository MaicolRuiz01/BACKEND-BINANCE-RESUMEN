package com.binance.web.BinanceAPI;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

import com.binance.web.BuyDollars.BuyDollars;
import com.binance.web.BuyDollars.BuyDollarsRepository;
import com.binance.web.BuyDollars.BuyDollarsService;
import com.binance.web.SellDollars.SellDollars;
import com.binance.web.SellDollars.SellDollarsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.binance.web.OrderP2P.*;
import com.binance.web.SaleP2P.SaleP2P;
import com.binance.web.SaleP2P.SaleP2PRepository;

@RestController
@RequestMapping("/api/spot-orders")
@RequiredArgsConstructor 
public class SpotOrdersController {

	private final BinanceService            binanceService;
    private final BuyDollarsService        buyDollarsService;
    private final SellDollarsRepository    sellDollarsRepository;
    private final BuyDollarsRepository     buyDollarsRepository;
    private final OrderP2PService          orderP2PService;
    private final SaleP2PRepository        saleP2PRepository;

    // ----------------- P2P Orders (filtradas) -----------------
    @GetMapping
    public ResponseEntity<List<OrderP2PDto>> getP2POrders(@RequestParam String account) {
        List<OrderP2PDto> ordenes = orderP2PService.showOrderP2PToday(account);

        Set<String> asignadas = saleP2PRepository.findAll().stream()
            .map(SaleP2P::getNumberOrder)
            .collect(Collectors.toSet());

        List<OrderP2PDto> sinAsignar = ordenes.stream()
            .filter(o -> !asignadas.contains(o.getOrderNumber()))
            .collect(Collectors.toList());

        return ResponseEntity.ok(sinAsignar);
    }
	    
	    
	    // Obtener depósitos en la billetera Spot
	    @GetMapping("/depositos")
	    public ResponseEntity<String> getSpotDeposits(
	            @RequestParam String account,
	            @RequestParam(defaultValue = "100") int limit) {

	        String response = binanceService.getSpotDeposits(account, limit);

	        try {
	            ObjectMapper mapper = new ObjectMapper();
	            JsonNode root = mapper.readTree(response);

	            // 1) IDs ya usados
	            Set<String> assignedIds = buyDollarsRepository.findAll().stream()
	                .map(bd -> bd.getIdDeposit())
	                .collect(Collectors.toSet());

	            // 2) Accedemos al array original (en data)
	            JsonNode dataNode = root.path("data");
	            if (!dataNode.isArray()) {
	                return ResponseEntity.ok(response);
	            }
	            ArrayNode sourceArray = (ArrayNode) dataNode;

	            // 3) Filtramos
	            ArrayNode filtered = mapper.createArrayNode();
	            for (JsonNode deposit : sourceArray) {
	                String id = deposit.path("id").asText();
	                if (!assignedIds.contains(id)) {
	                    filtered.add(deposit);
	                }
	            }

	            // 4) Reemplazamos "data" y devolvemos el JSON completo
	            ((ObjectNode) root).set("data", filtered);
	            return ResponseEntity.ok(mapper.writeValueAsString(root));

	        } catch (Exception e) {
	            // en caso de fallo devolvemos la respuesta original
	            return ResponseEntity.ok(response);
	        }
	    }
	

	    @GetMapping("/withdrawals")
	    public ResponseEntity<String> getSpotWithdrawals(
	            @RequestParam String account,
	            @RequestParam(defaultValue = "100") int limit) {

	        String response = binanceService.getSpotWithdrawals(account, limit);

	        try {
	            ObjectMapper mapper = new ObjectMapper();
	            JsonNode root = mapper.readTree(response);

	            // 1) Construimos la lista de IDs ya asignados
	            Set<String> assignedIds = sellDollarsRepository.findAll()
	                .stream()
	                .map(SellDollars::getIdWithdrawals)
	                .collect(Collectors.toSet());

	            ArrayNode sourceArray;
	            boolean rootIsArray = root.isArray();

	            // 2) Obtenemos el ArrayNode correcto según la forma del JSON
	            if (rootIsArray) {
	                sourceArray = (ArrayNode) root;
	            } else {
	                JsonNode dataNode = root.path("data");
	                if (!dataNode.isArray()) {
	                    // no hay array que filtrar, devolvemos original
	                    return ResponseEntity.ok(response);
	                }
	                sourceArray = (ArrayNode) dataNode;
	            }

	            // 3) Filtramos
	            ArrayNode filtered = mapper.createArrayNode();
	            for (JsonNode withdrawal : sourceArray) {
	                String id = withdrawal.path("id").asText();
	                if (!assignedIds.contains(id)) {
	                    filtered.add(withdrawal);
	                }
	            }

	            // 4) Reemplazamos en la respuesta
	            if (rootIsArray) {
	                // si era un array puro, devolvemos sólo el array filtrado
	                return ResponseEntity.ok(mapper.writeValueAsString(filtered));
	            } else {
	                // si era un objeto con data, sustituimos data
	                ((ObjectNode) root).set("data", filtered);
	                return ResponseEntity.ok(mapper.writeValueAsString(root));
	            }
	        } catch (Exception e) {
	            // ante cualquier error devolvemos la respuesta original
	            return ResponseEntity.ok(response);
	        }
	    }
	    
	    @GetMapping("/spot-balance")
	    public ResponseEntity<String> getSpotBalance(
	            @RequestParam String account,
	            @RequestParam String asset) {
	        return ResponseEntity.ok(binanceService.getSpotBalanceByAsset(account, asset));
	    }

	    

}

