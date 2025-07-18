package com.binance.web.BinanceAPI;



import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/p2p")
@CrossOrigin(origins = "*")
public class P2PController {

    private final BinanceService binanceService;

    public P2PController(BinanceService binanceService) {
        this.binanceService = binanceService;
    }

    @GetMapping("/orders")
    public ResponseEntity<String> getP2POrders(@RequestParam("account") String account) {
        return ResponseEntity.ok().body(binanceService.getP2POrderLatest(account));
    }

    @PostMapping("/anuncios")
public ResponseEntity<List<Map<String, Object>>> obtenerAnunciosFiltrados(@RequestBody Map<String, Object> filtros) {
    List<Map<String, Object>> anuncios = new ArrayList<>();
    anuncios.addAll(obtenerAnunciosPorTipo(filtros, "BUY"));
    anuncios.addAll(obtenerAnunciosPorTipo(filtros, "SELL"));

    return ResponseEntity.ok(anuncios);
}

private List<Map<String, Object>> obtenerAnunciosPorTipo(Map<String, Object> filtros, String tipo) {
    String url = "https://p2p.binance.com/bapi/c2c/v2/friendly/c2c/adv/search";
    RestTemplate restTemplate = new RestTemplate();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Accept-Encoding", "identity");

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("asset", filtros.getOrDefault("asset", "USDT"));
    requestBody.put("fiat", filtros.getOrDefault("fiat", "COP"));
    requestBody.put("tradeType", tipo);
    requestBody.put("payTypes", filtros.getOrDefault("payTypes", List.of()));
    requestBody.put("page", filtros.getOrDefault("page", 1));
    requestBody.put("rows", filtros.getOrDefault("rows", 10));
    requestBody.put("publisherType", filtros.getOrDefault("publisherType", null));

    HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    List<Map<String, Object>> anunciosFiltrados = new ArrayList<>();

    try {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode data = root.path("data");

        if (data.isArray()) {
            for (JsonNode item : data) {
                JsonNode adv = item.path("adv");
                JsonNode advertiser = item.path("advertiser");

                Map<String, Object> anuncio = new LinkedHashMap<>();
                anuncio.put("precio", adv.path("price").asText(""));
                anuncio.put("moneda", adv.path("asset").asText(""));
                anuncio.put("fiat", adv.path("fiatUnit").asText(""));
                anuncio.put("minimo", adv.path("minSingleTransAmount").asText(""));
                anuncio.put("maximo", adv.path("maxSingleTransAmount").asText(""));

                JsonNode tradeMethods = adv.path("tradeMethods");
                anuncio.put("metodoPago", tradeMethods.isArray() && tradeMethods.size() > 0
                        ? tradeMethods.get(0).path("tradeMethodName").asText("")
                        : "");

                anuncio.put("vendedor", advertiser.path("nickName").asText(""));
                anuncio.put("tipo", adv.path("tradeType").asText(""));

                anunciosFiltrados.add(anuncio);
            }
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return anunciosFiltrados;
}


}

