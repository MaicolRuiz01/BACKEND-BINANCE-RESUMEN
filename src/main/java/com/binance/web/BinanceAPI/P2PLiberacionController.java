package com.binance.web.BinanceAPI;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Liberar una venta P2P desde Ventas en curso (ver P2PLiberacionService).
 * Requiere sesión (JWT) como el resto del sistema; el usuario queda en el log.
 */
@RestController
@RequestMapping("/api/p2p/liberar")
@CrossOrigin(origins = "*")
public class P2PLiberacionController {

    private final P2PLiberacionService liberacionService;

    public P2PLiberacionController(P2PLiberacionService liberacionService) {
        this.liberacionService = liberacionService;
    }

    /** Body: {orderNumber, accountBinance, codigo} — codigo = 6 dígitos de Google Authenticator. */
    @PostMapping
    public ResponseEntity<?> liberar(@RequestBody Map<String, String> body, Principal principal) {
        String operador = principal != null ? principal.getName() : "desconocido";
        return ResponseEntity.ok(liberacionService.liberar(
                body.get("orderNumber"), body.get("accountBinance"), body.get("codigo"), operador));
    }
}
