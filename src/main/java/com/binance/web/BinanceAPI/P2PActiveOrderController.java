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
        String orderNumber    = (String) body.get("orderNumber");
        Integer copId         = (Integer) body.get("copId");
        String accountBinance = (String) body.get("accountBinance");

        if (orderNumber == null || copId == null || accountBinance == null) {
            // Se detalla QUÉ falta: antes solo decía "faltan campos" y no había forma de saber
            // cuál, ni desde el log ni desde la pantalla del operador.
            String faltantes = (orderNumber == null ? "orderNumber " : "")
                    + (copId == null ? "copId " : "")
                    + (accountBinance == null ? "accountBinance" : "");
            log.warn("[PreAsign] Petición incompleta — faltan: {}", faltantes.trim());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Faltan datos de la orden (" + faltantes.trim() + ")"));
        }

        try {
            activeOrderService.upsertPreAsignacion(orderNumber, copId, accountBinance);
            return ResponseEntity.ok(Map.of("mensaje", "Pre-asignación guardada"));
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            // CARRERA: otro hilo (o el auto-sync) insertó la misma orden a la vez → chocó el unique.
            // Reintentamos: ahora la fila YA existe, así el upsert entra por la rama de UPDATE
            // y no vuelve a insertar. Esto evita el error "Duplicate entry" al asignar órdenes.
            try {
                activeOrderService.upsertPreAsignacion(orderNumber, copId, accountBinance);
                return ResponseEntity.ok(Map.of("mensaje", "Pre-asignación guardada"));
            } catch (Exception e2) {
                log.warn("[PreAsign] Reintento tras duplicado: {}", e2.getMessage());
                return ResponseEntity.ok(Map.of("mensaje", "Pre-asignación ya existía"));
            }
        // PessimisticLockingFailureException cubre tanto CannotAcquireLockException (se venció el
        // tiempo de espera del candado) como DeadlockLoserDataAccessException (interbloqueo),
        // que son las dos formas en que MySQL reporta esto. No se pueden poner ambas en un
        // multi-catch porque son subclases de esta.
        } catch (org.springframework.dao.PessimisticLockingFailureException lock) {
            // BLOQUEO de base de datos: otra transacción (típicamente el sync de Binance) tenía
            // tomada la fila de la cuenta COP. No es un error del operador ni de sus datos, así
            // que se reintenta un par de veces con una pausa corta antes de rendirse.
            for (int intento = 1; intento <= 2; intento++) {
                try {
                    Thread.sleep(300L * intento);
                    activeOrderService.upsertPreAsignacion(orderNumber, copId, accountBinance);
                    log.info("[PreAsign] Orden {} guardada en el reintento {} tras bloqueo", orderNumber, intento);
                    return ResponseEntity.ok(Map.of("mensaje", "Pre-asignación guardada"));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e2) {
                    log.warn("[PreAsign] Reintento {} de la orden {} falló: {}",
                            intento, orderNumber, e2.getMessage());
                }
            }
            log.error("[PreAsign] Orden {} → cuenta {}: bloqueo persistente", orderNumber, copId, lock);
            return ResponseEntity.badRequest().body(Map.of("error",
                    "La base de datos está ocupada en este momento. Espera unos segundos y vuelve a intentar."));

        } catch (Exception e) {
            // Se registra el TIPO de excepción y la traza: con solo getMessage() muchas
            // excepciones de JPA llegan con mensaje vacío y el log no servía para nada.
            log.error("[PreAsign] Falló la orden {} → cuenta {}: {} - {}",
                    orderNumber, copId, e.getClass().getSimpleName(), e.getMessage(), e);
            String detalle = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo guardar: " + detalle));
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
