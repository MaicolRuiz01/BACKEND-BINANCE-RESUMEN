package com.binance.web.BinanceAPI;

import com.binance.web.dto.ActiveP2POrderDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints para órdenes P2P activas y pre-asignaciones de cuenta COP.
 */
@Slf4j
@RestController
@RequestMapping("/api/p2p")
@CrossOrigin("*")
public class P2PActiveOrderController {

    @Autowired private P2PActiveOrderService activeOrderService;

    // ─────────────────────────────────────────────────────────────
    // Órdenes activas
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/p2p/active-orders
     * Retorna todas las órdenes en TRADING / BUYER_PAYED de todas las cuentas.
     * Incluye la pre-asignación si existe para cada orden.
     */
    @GetMapping("/active-orders")
    public ResponseEntity<List<ActiveP2POrderDto>> getActiveOrders() {
        return ResponseEntity.ok(activeOrderService.getAllActiveOrders());
    }

    /**
     * GET /api/p2p/active-orders/{account}
     * Retorna órdenes activas solo de una cuenta específica.
     */
    @GetMapping("/active-orders/{account}")
    public ResponseEntity<?> getActiveOrdersByAccount(@PathVariable String account) {
        try {
            return ResponseEntity.ok(activeOrderService.getActiveOrdersForAccount(account));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Pre-asignaciones
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/p2p/pre-asignacion
     * Body: { "orderNumber": "...", "copId": 3, "accountBinance": "cuenta1" }
     * Crea o actualiza la pre-asignación de esa orden a una cuenta COP.
     */
    @PostMapping("/pre-asignacion")
    public ResponseEntity<?> upsertPreAsignacion(@RequestBody Map<String, Object> body) {
        try {
            String orderNumber   = (String) body.get("orderNumber");
            Integer copId        = (Integer) body.get("copId");
            String accountBinance = (String) body.get("accountBinance");

            if (orderNumber == null || copId == null || accountBinance == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Faltan campos requeridos"));
            }

            activeOrderService.upsertPreAsignacion(orderNumber, copId, accountBinance);
            return ResponseEntity.ok(Map.of("mensaje", "Pre-asignación guardada"));
        } catch (Exception e) {
            log.error("[PreAsign] Error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/p2p/pre-asignacion/{orderNumber}
     * Elimina la pre-asignación de esa orden (queda sin cuenta COP asignada).
     */
    @DeleteMapping("/pre-asignacion/{orderNumber}")
    public ResponseEntity<?> deletePreAsignacion(@PathVariable String orderNumber) {
        activeOrderService.deletePreAsignacion(orderNumber);
        return ResponseEntity.ok(Map.of("mensaje", "Pre-asignación eliminada"));
    }

    /**
     * PUT /api/p2p/pre-asignacion/{orderNumber}/estado?estado=RECIBIDO|PENDIENTE
     * Clasifica manualmente el dinero de la orden: RECIBIDO (verde) o PENDIENTE (amarillo).
     */
    @org.springframework.web.bind.annotation.PutMapping("/pre-asignacion/{orderNumber}/estado")
    public ResponseEntity<?> setEstadoManual(@PathVariable String orderNumber,
                                             @org.springframework.web.bind.annotation.RequestParam String estado) {
        try {
            activeOrderService.setEstadoManual(orderNumber, estado);
            return ResponseEntity.ok(Map.of("mensaje", "Estado actualizado"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
