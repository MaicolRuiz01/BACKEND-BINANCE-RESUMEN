package com.binance.web.serviceImpl;

import com.binance.web.service.CuentasP2PTelegramService;
import com.binance.web.util.HttpClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementación mínima (solo envío) del bot "Cuentas P2P" — mismo patrón de
 * llamada a la API de Telegram que TelegramServiceImpl, pero con su propio
 * token (app.cuentasp2p.bot-token), para que sea un bot completamente
 * independiente del de retiradores/conciliación.
 */
@Slf4j
@Service
public class CuentasP2PTelegramServiceImpl implements CuentasP2PTelegramService {

    private final RestTemplate restTemplate = HttpClientFactory.timed();

    @Value("${app.cuentasp2p.bot-token:}")
    private String botToken;

    @Override
    public Integer sendMessage(String chatId, String message) {
        if (!isConfigured(chatId))
            return null;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", message);
            payload.put("parse_mode", "Markdown");

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) post("/sendMessage", payload);
            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                Integer messageId = (Integer) result.get("message_id");
                log.info("[CuentasP2P] Mensaje enviado a chat: {}, message_id: {}", chatId, messageId);
                return messageId;
            } else {
                log.warn("[CuentasP2P] Telegram respondió sin 'ok' al mandar a {}: {}", chatId, response);
            }
        } catch (Exception e) {
            log.error("[CuentasP2P] Error al enviar mensaje a {}: {}", chatId, e.getMessage());
        }
        return null;
    }

    private boolean isConfigured(String chatId) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("[CuentasP2P] Bot token no configurado (app.cuentasp2p.bot-token) — acción omitida.");
            return false;
        }
        if (chatId == null || chatId.isBlank()) {
            log.warn("[CuentasP2P] Chat ID vacío — acción omitida.");
            return false;
        }
        return true;
    }

    private Object post(String endpoint, Map<String, Object> payload) {
        String url = "https://api.telegram.org/bot" + botToken + endpoint;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForObject(url, new HttpEntity<>(payload, headers), Object.class);
    }
}
