package com.binance.web.BinanceAPI;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /** Búsqueda general de anuncios en el marketplace de Binance P2P */
    @PostMapping("/anuncios")
    public ResponseEntity<List<AnuncioDto>> obtenerAnunciosFiltrados(@RequestBody Map<String, Object> filtros) {
        return ResponseEntity.ok(binanceService.obtenerTodosLosAnuncios(filtros));
    }

    /** Devuelve únicamente los anuncios activos de las cuentas propias del cliente */
    @GetMapping("/mis-anuncios")
    public ResponseEntity<List<AnuncioDto>> obtenerMisAnuncios() {
        return ResponseEntity.ok(binanceService.obtenerMisAnuncios());
    }
}
