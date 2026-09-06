package com.binance.web.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Procesa los updates que Telegram manda para el bot "Cuentas P2P" — hoy en
 * día SOLO maneja el botón "✅" de las notificaciones de movimiento (ver
 * MovimientosBridgeServiceImpl → sendMessageConBotonLiberar), agregado
 * 05/09/2026 a pedido de Milton para poder marcar visualmente qué ventas ya
 * se liberaron cuando el chat se llena de transferencias.
 *
 * A propósito NO hace falta guardar nada en base de datos para esto: el
 * callback_query que manda Telegram ya trae el chat_id, message_id y el
 * texto/entities del mensaje original, así que basta con editar ESE mensaje
 * directamente — no hay ninguna otra parte del sistema que necesite saber
 * qué se marcó como liberado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CuentasP2PTelegramWebhookService {

    private static final String CALLBACK_LIBERAR = "liberar";

    private final CuentasP2PTelegramService cuentasP2PTelegramService;

    @SuppressWarnings("unchecked")
    public void process(Map<String, Object> update) {
        if (update == null) return;

        Object callbackQueryObj = update.get("callback_query");
        if (!(callbackQueryObj instanceof Map)) {
            // Este bot es de solo botones — cualquier otro tipo de update
            // (mensajes de texto, /start, etc.) se ignora a propósito.
            return;
        }

        Map<String, Object> callbackQuery = (Map<String, Object>) callbackQueryObj;
        String callbackQueryId = (String) callbackQuery.get("id");
        String data = (String) callbackQuery.get("data");

        if (!CALLBACK_LIBERAR.equals(data)) {
            log.warn("[CuentasP2P Webhook] callback_data desconocido: {}", data);
            cuentasP2PTelegramService.answerCallbackQuery(callbackQueryId);
            return;
        }

        Map<String, Object> message = (Map<String, Object>) callbackQuery.get("message");
        if (message == null) {
            cuentasP2PTelegramService.answerCallbackQuery(callbackQueryId);
            return;
        }

        Map<String, Object> chat = (Map<String, Object>) message.get("chat");
        String chatId = chat != null && chat.get("id") != null ? String.valueOf(chat.get("id")) : null;
        Integer messageId = (Integer) message.get("message_id");
        String textoOriginal = (String) message.get("text");
        List<Map<String, Object>> entities = (List<Map<String, Object>>) message.get("entities");

        if (chatId == null || messageId == null) {
            log.warn("[CuentasP2P Webhook] Callback 'liberar' sin chat_id/message_id — no se puede editar.");
            cuentasP2PTelegramService.answerCallbackQuery(callbackQueryId);
            return;
        }

        cuentasP2PTelegramService.marcarComoLiberado(chatId, messageId, textoOriginal, entities);
        cuentasP2PTelegramService.answerCallbackQuery(callbackQueryId);

        log.info("[CuentasP2P Webhook] Mensaje {} en chat {} marcado como liberado.", messageId, chatId);
    }
}
