package com.binance.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Al arrancar el backend, registra automáticamente el webhook de Telegram.
 * Solo actúa si app.telegram.webhook-url está configurado (no vacío).
 * En desarrollo local se deja vacío para evitar registrar localhost.
 */
@Slf4j
@Component
public class TelegramWebhookRegistrar {

    @Value("${app.telegram.bot-token:}")
    private String botToken;

    @Value("${app.telegram.webhook-url:}")
    private String webhookUrl;

    @EventListener(ApplicationReadyEvent.class)
    public void registerWebhook() {
        if (botToken.isBlank() || webhookUrl.isBlank()) {
            log.info("[Telegram] webhook-url no configurado — registro omitido (OK en desarrollo local).");
            return;
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://api.telegram.org/bot" + botToken + "/setWebhook";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of("url", webhookUrl);

            restTemplate.postForObject(url, new HttpEntity<>(body, headers), Object.class);
            log.info("[Telegram] Webhook registrado en: {}", webhookUrl);
        } catch (Exception e) {
            log.error("[Telegram] Error al registrar webhook: {}", e.getMessage());
        }
    }
}
