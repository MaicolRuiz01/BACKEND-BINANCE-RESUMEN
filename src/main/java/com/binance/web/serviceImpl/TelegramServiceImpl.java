package com.binance.web.serviceImpl;

import com.binance.web.service.TelegramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TelegramServiceImpl implements TelegramService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.telegram.bot-token:}")
    private String botToken;

    @Override
    public void sendMessage(String chatId, String message) {
        if (!isConfigured(chatId)) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", message);
            payload.put("parse_mode", "Markdown");
            post("/sendMessage", payload);
            log.info("[Telegram] Mensaje enviado a chat: {}", chatId);
        } catch (Exception e) {
            log.error("[Telegram] Error al enviar mensaje: {}", e.getMessage());
        }
    }

    @Override
    public Integer sendMessageWithButton(String chatId, String text, String buttonLabel, String callbackData) {
        if (!isConfigured(chatId)) return null;
        try {
            Map<String, Object> button = new HashMap<>();
            button.put("text", buttonLabel);
            button.put("callback_data", callbackData);

            Map<String, Object> replyMarkup = new HashMap<>();
            replyMarkup.put("inline_keyboard", List.of(List.of(button)));

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", text);
            payload.put("parse_mode", "Markdown");
            payload.put("reply_markup", replyMarkup);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) post("/sendMessage", payload);
            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                Integer messageId = (Integer) result.get("message_id");
                log.info("[Telegram] Mensaje con botón enviado a {}, message_id={}", chatId, messageId);
                return messageId;
            }
        } catch (Exception e) {
            log.error("[Telegram] Error al enviar mensaje con botón: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public void editMessage(String chatId, Integer messageId, String newText) {
        if (messageId == null || !isConfigured(chatId)) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("message_id", messageId);
            payload.put("text", newText);
            payload.put("parse_mode", "Markdown");
            post("/editMessageText", payload);
            log.info("[Telegram] Mensaje {} editado en chat {}", messageId, chatId);
        } catch (Exception e) {
            log.error("[Telegram] Error al editar mensaje: {}", e.getMessage());
        }
    }

    @Override
    public void deleteMessage(String chatId, Integer messageId) {
        if (messageId == null || !isConfigured(chatId)) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("message_id", messageId);
            post("/deleteMessage", payload);
            log.info("[Telegram] Mensaje {} eliminado del chat {}", messageId, chatId);
        } catch (Exception e) {
            log.error("[Telegram] Error al eliminar mensaje: {}", e.getMessage());
        }
    }

    @Override
    public Integer sendMessageWithTwoButtons(String chatId, String text,
            String label1, String callbackData1,
            String label2, String callbackData2) {
        if (!isConfigured(chatId)) return null;
        try {
            Map<String, Object> btn1 = new HashMap<>();
            btn1.put("text", label1);
            btn1.put("callback_data", callbackData1);

            Map<String, Object> btn2 = new HashMap<>();
            btn2.put("text", label2);
            btn2.put("callback_data", callbackData2);

            Map<String, Object> replyMarkup = new HashMap<>();
            replyMarkup.put("inline_keyboard", List.of(List.of(btn1), List.of(btn2)));

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", text);
            payload.put("parse_mode", "Markdown");
            payload.put("reply_markup", replyMarkup);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) post("/sendMessage", payload);
            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) response.get("result");
                Integer messageId = (Integer) result.get("message_id");
                log.info("[Telegram] Mensaje con 2 botones enviado a {}, message_id={}", chatId, messageId);
                return messageId;
            }
        } catch (Exception e) {
            log.error("[Telegram] Error al enviar mensaje con 2 botones: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public void answerCallbackQuery(String callbackQueryId, String text) {
        if (botToken == null || botToken.isBlank()) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("callback_query_id", callbackQueryId);
            payload.put("text", text);
            payload.put("show_alert", true);
            post("/answerCallbackQuery", payload);
        } catch (Exception e) {
            log.error("[Telegram] Error al responder callback: {}", e.getMessage());
        }
    }

    private boolean isConfigured(String chatId) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("[Telegram] Bot token no configurado — acción omitida.");
            return false;
        }
        if (chatId == null || chatId.isBlank()) {
            log.warn("[Telegram] Chat ID vacío — acción omitida.");
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
