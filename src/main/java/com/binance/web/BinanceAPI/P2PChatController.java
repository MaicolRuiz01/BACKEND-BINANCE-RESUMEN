package com.binance.web.BinanceAPI;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Chat de órdenes P2P (ver P2PChatService). Lo usa la pestaña "Ventas en curso" para enviarle
 * al cliente la cuenta COP a la que debe consignar, y el panel de prueba del chat.
 */
@RestController
@RequestMapping("/api/p2p/chat")
@CrossOrigin(origins = "*")
public class P2PChatController {

    private final P2PChatService chatService;

    public P2PChatController(P2PChatService chatService) {
        this.chatService = chatService;
    }

    /** Estado del envío automático de la cuenta COP al asignar. */
    @GetMapping("/auto-envio")
    public ResponseEntity<?> getAutoEnvio() {
        return ResponseEntity.ok(Map.of("activo", chatService.isAutoEnvio()));
    }

    /** Prende/apaga el envío automático. Body: {"activo": true|false}. */
    @PutMapping("/auto-envio")
    public ResponseEntity<?> setAutoEnvio(@RequestBody Map<String, Boolean> body) {
        boolean activo = Boolean.TRUE.equals(body.get("activo"));
        return ResponseEntity.ok(Map.of("activo", chatService.setAutoEnvio(activo)));
    }

    /**
     * Resumen del chat de las ventas en curso: mensajes del cliente (para el aviso de "nuevo")
     * y estado del envío automático de la cuenta. Body: [{orderNumber, accountBinance}, ...].
     */
    @PostMapping("/resumen")
    public ResponseEntity<?> resumen(@RequestBody List<Map<String, String>> ordenes) {
        if (ordenes == null || ordenes.isEmpty()) return ResponseEntity.ok(List.of());
        // Tope defensivo: nunca leer de golpe el chat de demasiadas órdenes.
        return ResponseEntity.ok(chatService.resumen(ordenes.size() > 40 ? ordenes.subList(0, 40) : ordenes));
    }

    /** Mensaje sugerido con los datos de la cuenta COP pre-asignada a la orden. */
    @GetMapping("/mensaje/{orderNumber}")
    public ResponseEntity<?> mensajeSugerido(@PathVariable String orderNumber) {
        try {
            return ResponseEntity.ok(Map.of("texto", chatService.mensajeSugerido(orderNumber)));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Órdenes de la cuenta en las últimas horas (incluye terminadas), para elegir a cuál escribir. */
    @GetMapping("/ordenes-recientes")
    public ResponseEntity<?> ordenesRecientes(@RequestParam("account") String account,
                                              @RequestParam(value = "horas", defaultValue = "24") int horas) {
        try {
            return ResponseEntity.ok(chatService.ordenesRecientes(account, horas));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    e.getMessage() != null ? e.getMessage() : "No se pudo leer el historial de Binance."));
        }
    }

    /** Conversación de una orden (últimos {@code rows} mensajes, del más viejo al más nuevo). */
    @GetMapping("/mensajes")
    public ResponseEntity<?> mensajes(@RequestParam("account") String account,
                                      @RequestParam("orderNo") String orderNo,
                                      @RequestParam(value = "rows", defaultValue = "50") int rows) {
        try {
            return ResponseEntity.ok(chatService.mensajes(account, orderNo, rows));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    e.getMessage() != null ? e.getMessage() : "No se pudo leer el chat de la orden."));
        }
    }

    /** Envía un mensaje al chat de la orden. Body: {accountBinance, orderNumber, texto}. */
    @PostMapping("/enviar")
    public ResponseEntity<?> enviar(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(chatService.enviar(
                    body.get("accountBinance"), body.get("orderNumber"), body.get("texto")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Solo escucha el chat de una cuenta unos segundos. Body: {accountBinance, segundos}. */
    @PostMapping("/escuchar")
    public ResponseEntity<?> escuchar(@RequestBody Map<String, Object> body) {
        Object cuenta = body.get("accountBinance");
        if (cuenta == null || cuenta.toString().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Falta la cuenta Binance."));
        }
        int segundos = body.get("segundos") instanceof Number n ? n.intValue() : 20;
        return ResponseEntity.ok(chatService.escuchar(cuenta.toString(), segundos));
    }
}
