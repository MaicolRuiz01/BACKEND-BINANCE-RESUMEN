package com.binance.web.controller;

import com.binance.web.service.TelegramWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Recibe los updates de Telegram vía webhook (POST /telegram/webhook).
 * Telegram llama a esta URL cada vez que hay un mensaje o un botón presionado.
 * Debe responder HTTP 200 rápidamente para que Telegram no reintente.
 */
@Slf4j
@RestController
@RequestMapping("/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramWebhookService webhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> update) {
        log.debug("[Webhook] Update recibido: {}", update);
        try {
            webhookService.process(update);
        } catch (Exception e) {
            // Siempre 200 para que Telegram no reintente con el mismo update
            log.error("[Webhook] Error procesando update: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}
