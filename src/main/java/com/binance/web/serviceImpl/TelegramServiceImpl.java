package com.binance.web.serviceImpl;

import com.binance.web.service.TelegramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.binance.web.util.HttpClientFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TelegramServiceImpl implements TelegramService {

    private final RestTemplate restTemplate = HttpClientFactory.timed();

    @Value("${app.telegram.bot-token:}")
    private String botToken;

    // ─────────────────────────────────────────────────────────────────────────
    // sendMessage — texto simple
    // ─────────────────────────────────────────────────────────────────────────

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
                log.info("[Telegram] Mensaje enviado a chat: {}, message_id: {}", chatId, messageId);
                return messageId;
            }
        } catch (Exception e) {
            log.error("[Telegram] Error al enviar mensaje: {}", e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sendMessageWithButton — mensaje con un botón inline, retorna message_id
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Integer sendMessageWithButton(String chatId, String text, String buttonLabel, String callbackData) {
        if (!isConfigured(chatId))
            return null;
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

    // ─────────────────────────────────────────────────────────────────────────
    // sendMessageWithTwoButtons — mensaje con dos botones inline (uno por fila)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Integer sendMessageWithTwoButtons(String chatId, String text,
            String btn1Text, String btn1Data,
            String btn2Text, String btn2Data) {
        if (!isConfigured(chatId))
            return null;
        try {
            Map<String, Object> button1 = new HashMap<>();
            button1.put("text", btn1Text);
            button1.put("callback_data", btn1Data);

            Map<String, Object> button2 = new HashMap<>();
            button2.put("text", btn2Text);
            button2.put("callback_data", btn2Data);

            Map<String, Object> replyMarkup = new HashMap<>();
            // Un botón por fila para mejor visibilidad
            replyMarkup.put("inline_keyboard", List.of(List.of(button1), List.of(button2)));

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
                log.info("[Telegram] Mensaje con dos botones enviado a {}, message_id={}", chatId, messageId);
                return messageId;
            }
        } catch (Exception e) {
            log.error("[Telegram] Error al enviar mensaje con dos botones: {}", e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sendMessageWithButtons — mensaje NUEVO con N botones dinámicos (uno por fila)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Integer sendMessageWithButtons(String chatId, String text, Map<String, String> buttonsData) {
        if (!isConfigured(chatId))
            return null;
        try {
            List<List<Map<String, Object>>> inlineKeyboard = new java.util.ArrayList<>();
            for (Map.Entry<String, String> entry : buttonsData.entrySet()) {
                Map<String, Object> button = new HashMap<>();
                button.put("text", entry.getKey());
                button.put("callback_data", entry.getValue());
                inlineKeyboard.add(List.of(button));
            }

            Map<String, Object> replyMarkup = new HashMap<>();
            replyMarkup.put("inline_keyboard", inlineKeyboard);

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
                log.info("[Telegram] Mensaje con botones dinámicos enviado a {}, message_id={}", chatId, messageId);
                return messageId;
            }
        } catch (Exception e) {
            log.error("[Telegram] Error al enviar mensaje con botones dinámicos: {}", e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // editMessage — edita texto de un mensaje existente (mantiene botones)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void editMessage(String chatId, Integer messageId, String newText) {
        if (messageId == null || !isConfigured(chatId))
            return;
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

    // ─────────────────────────────────────────────────────────────────────────
    // editMessageWithDynamicButtons — edita el mensaje agregando botones dinámicos
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void editMessageWithDynamicButtons(String chatId, Integer messageId, String text, Map<String, String> buttonsData) {
        if (messageId == null || !isConfigured(chatId)) return;
        try {
            // Construir el teclado (1 botón por fila para que se lean bien los nombres largos)
            List<List<Map<String, Object>>> inlineKeyboard = new java.util.ArrayList<>();
            for (Map.Entry<String, String> entry : buttonsData.entrySet()) {
                Map<String, Object> button = new HashMap<>();
                button.put("text", entry.getKey());
                button.put("callback_data", entry.getValue());
                inlineKeyboard.add(List.of(button));
            }

            Map<String, Object> replyMarkup = new HashMap<>();
            replyMarkup.put("inline_keyboard", inlineKeyboard);

            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("message_id", messageId);
            payload.put("text", text);
            payload.put("parse_mode", "Markdown");
            payload.put("reply_markup", replyMarkup);

            post("/editMessageText", payload);
            log.info("[Telegram] Mensaje {} editado con botones dinámicos en chat {}", messageId, chatId);
        } catch (Exception e) {
            log.error("[Telegram] Error al editar mensaje con botones dinámicos: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // editMessageTextOnly — edita solo el texto y elimina los botones inline
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void editMessageTextOnly(String chatId, Integer messageId, String newText) {
        if (messageId == null || !isConfigured(chatId))
            return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("message_id", messageId);
            payload.put("text", newText);
            payload.put("parse_mode", "Markdown");
            // Al no incluir reply_markup, Telegram elimina los botones inline
            // automáticamente.

            post("/editMessageText", payload);
            log.info("[Telegram] Mensaje {} editado a solo texto en chat {}", messageId, chatId);
        } catch (Exception e) {
            log.error("[Telegram] Error al editar mensaje a solo texto: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteMessage — elimina un mensaje del chat
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void deleteMessage(String chatId, Integer messageId) {
        if (messageId == null || !isConfigured(chatId))
            return;
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

    // ─────────────────────────────────────────────────────────────────────────
    // answerCallbackQuery — responde al click del botón (Telegram lo requiere)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void answerCallbackQuery(String callbackQueryId, String text) {
        if (botToken == null || botToken.isBlank())
            return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("callback_query_id", callbackQueryId);

            // Telegram no permite un "alert" (popup) sin texto — si se manda
            // show_alert=true con text vacío, el callback nunca queda
            // correctamente respondido y el cliente termina mostrando su propio
            // diálogo genérico de "Error". Por eso solo mostramos el popup
            // cuando en verdad hay un mensaje para el usuario.
            boolean tieneTexto = text != null && !text.isBlank();
            if (tieneTexto) {
                payload.put("text", text);
            }
            payload.put("show_alert", tieneTexto);

            post("/answerCallbackQuery", payload);
        } catch (Exception e) {
            log.error("[Telegram] Error al responder callback: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers internos
    // ─────────────────────────────────────────────────────────────────────────

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
