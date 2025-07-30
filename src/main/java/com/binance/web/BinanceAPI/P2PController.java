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
    public ResponseEntity<List<AnuncioDto>> obtenerAnunciosFiltrados(@RequestBody Map<String, Object> filtros) {
        List<AnuncioDto> anuncios = binanceService.obtenerTodosLosAnuncios(filtros);
        return ResponseEntity.ok(anuncios);
    }

	/*
	 * @GetMapping("/anuncios/mis-cuentas") public ResponseEntity<List<AnuncioDto>>
	 * obtenerAnunciosDeMisCuentas() { List<AnuncioDto> anuncios =
	 * binanceService.getOwnPublishedAds(); return ResponseEntity.ok(anuncios); }
	 */

}
