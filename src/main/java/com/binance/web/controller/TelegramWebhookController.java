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
    private final com.binance.web.service.TelegramUpdateDispatcher dispatcher;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> update) {
        log.debug("[Webhook] Update recibido: {}", update);
        // Se responde 200 AL INSTANTE y el update se procesa en segundo plano (ver TelegramUpdateDispatcher): aunque la
        // base de datos o Telegram vayan lentos, el webhook nunca se queda esperando y Telegram no reintenta ni acumula.
        dispatcher.despachar(update, () -> webhookService.process(update));
        return ResponseEntity.ok().build();
    }
}
