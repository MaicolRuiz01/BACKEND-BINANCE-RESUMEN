package com.binance.web.service;

public interface TelegramService {

    /** Envía un mensaje de texto simple a un chat. */
    void sendMessage(String chatId, String message);

    /**
     * Envía un mensaje con un botón inline al grupo.
     * @return message_id del mensaje enviado (para poder editarlo/borrarlo después).
     */
    Integer sendMessageWithButton(String chatId, String text, String buttonLabel, String callbackData);

    /** Edita el texto de un mensaje ya enviado (para marcar la solicitud como tomada). */
    void editMessage(String chatId, Integer messageId, String newText);

    /** Elimina un mensaje de un chat. */
    void deleteMessage(String chatId, Integer messageId);

    /**
     * Responde a un callback_query de Telegram (aparece como toast/popup al usuario).
     * Obligatorio llamarlo siempre que se recibe un callback, o Telegram mostrará el spinner indefinidamente.
     */
    void answerCallbackQuery(String callbackQueryId, String text);
}
