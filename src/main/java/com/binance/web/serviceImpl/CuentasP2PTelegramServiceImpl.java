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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    @Override
    public Integer sendMessageConBotonLiberar(String chatId, String message) {
        if (!isConfigured(chatId))
            return null;
        try {
            Map<String, Object> boton = new HashMap<>();
            boton.put("text", "✅");
            boton.put("callback_data", "liberar");

            List<List<Map<String, Object>>> inlineKeyboard = new ArrayList<>();
            inlineKeyboard.add(List.of(boton));

            Map<String, Object> replyMarkup = new HashMap<>();
            replyMarkup.put("inline_keyboard", inlineKeyboard);

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", message);
            payload.put("parse_mode", "Markdown");
            payload.put("reply_markup", replyMarkup);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) post("/sendMessage", payload);
            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                Integer messageId = (Integer) result.get("message_id");
                log.info("[CuentasP2P] Mensaje con botón 'Liberar' enviado a chat: {}, message_id: {}", chatId, messageId);
                return messageId;
            } else {
                log.warn("[CuentasP2P] Telegram respondió sin 'ok' al mandar (con botón) a {}: {}", chatId, response);
            }
        } catch (Exception e) {
            log.error("[CuentasP2P] Error al enviar mensaje con botón a {}: {}", chatId, e.getMessage());
        }
        return null;
    }

    @Override
    public void marcarComoLiberado(String chatId, Integer messageId, String textoOriginal, List<Map<String, Object>> entities) {
        if (messageId == null || !isConfigured(chatId)) return;
        try {
            String textoFinal = (textoOriginal == null ? "" : textoOriginal) + "\n\n✅ Liberada";

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("message_id", messageId);
            payload.put("text", textoFinal);
            // Se pasan las entities (bold/code) del mensaje ORIGINAL tal cual —
            // solo describen el texto de antes, que no se tocó (solo se le
            // agregó una línea nueva al final) — en vez de parse_mode, para no
            // arriesgarse a que un caracter especial en la descripción del
            // banco (ej. un guion bajo) rompa el parseo de Markdown al reenviar.
            if (entities != null && !entities.isEmpty()) {
                payload.put("entities", entities);
            }
            // reply_markup vacío = Telegram quita el botón (ya cumplió su propósito).
            Map<String, Object> replyMarkup = new HashMap<>();
            replyMarkup.put("inline_keyboard", List.of());
            payload.put("reply_markup", replyMarkup);

            post("/editMessageText", payload);
            log.info("[CuentasP2P] Mensaje {} marcado como liberado en chat {}", messageId, chatId);
        } catch (Exception e) {
            log.error("[CuentasP2P] Error al marcar mensaje {} como liberado: {}", messageId, e.getMessage());
        }
    }

    @Override
    public void answerCallbackQuery(String callbackQueryId) {
        if (callbackQueryId == null || botToken == null || botToken.isBlank()) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("callback_query_id", callbackQueryId);
            post("/answerCallbackQuery", payload);
        } catch (Exception e) {
            log.error("[CuentasP2P] Error al responder callback: {}", e.getMessage());
        }
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
