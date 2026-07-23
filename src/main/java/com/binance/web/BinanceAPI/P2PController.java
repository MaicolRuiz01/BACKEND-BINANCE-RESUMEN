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

    /**
     * DIAGNÓSTICO (solo lectura): vuelca CRUDO el historial C2C de una cuenta para un día
     * concreto (todos los tradeType y todos los orderStatus, sin filtrar). Sirve para localizar
     * movimientos raros como el "pago atrasado" de una apelación y ver con qué campos vienen.
     * Ej: GET /api/p2p/orders-fecha?account=Luis&fecha=2026-07-17
     */
    @GetMapping("/orders-fecha")
    public ResponseEntity<String> getP2POrdersPorFecha(@RequestParam("account") String account,
                                                       @RequestParam("fecha") String fecha) {
        java.time.ZoneId zona = java.time.ZoneId.of("America/Bogota");
        java.time.LocalDate dia = java.time.LocalDate.parse(fecha);
        long start = dia.atStartOfDay(zona).toInstant().toEpochMilli();
        long end   = dia.plusDays(1).atStartOfDay(zona).toInstant().toEpochMilli();
        // tradeType null → trae BUY y SELL; sin filtrar estado → incluye CANCELLED, IN_APPEAL, etc.
        return ResponseEntity.ok().body(binanceService.getP2POrdersInRange(account, start, end, null));
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
