package com.binance.web.controller;

import com.binance.web.service.CuentasP2PTelegramWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Recibe los updates de Telegram del bot "Cuentas P2P" vía webhook
 * (POST /cuentasp2p/telegram/webhook) — hoy en día solo el botón "✅"
 * de las notificaciones de movimiento (ver CuentasP2PTelegramWebhookService).
 *
 * Separado de TelegramWebhookController/telegram/webhook a propósito: ese es
 * el webhook del bot de RETIRADORES (token distinto) — cada bot de Telegram
 * necesita su propia URL de webhook registrada con SU propio token, así que
 * no pueden compartir el mismo endpoint.
 *
 * Debe responder HTTP 200 rápido para que Telegram no reintente.
 */
@Slf4j
@RestController
@RequestMapping("/cuentasp2p/telegram")
@RequiredArgsConstructor
public class CuentasP2PTelegramWebhookController {

    private final CuentasP2PTelegramWebhookService webhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> update) {
        log.debug("[CuentasP2P Webhook] Update recibido: {}", update);
        try {
            webhookService.process(update);
        } catch (Exception e) {
            // Siempre 200 para que Telegram no reintente con el mismo update.
            log.error("[CuentasP2P Webhook] Error procesando update: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}
