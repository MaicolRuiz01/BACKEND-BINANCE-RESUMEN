package com.binance.web.service;

public interface TelegramService {

    /** Envía un mensaje de texto simple a un chat. */
    void sendMessage(String chatId, String message);

    /**
     * Envía un mensaje con un botón inline.
     * @return message_id del mensaje enviado (para poder editarlo después).
     */
    Integer sendMessageWithButton(String chatId, String text, String buttonLabel, String callbackData);

    /** Edita el texto de un mensaje ya enviado. */
    void editMessage(String chatId, Integer messageId, String newText);

    /** Elimina un mensaje de un chat. */
    void deleteMessage(String chatId, Integer messageId);

    /**
     * Envía un mensaje con dos botones inline (en filas separadas).
     * @return message_id del mensaje enviado.
     */
    Integer sendMessageWithTwoButtons(String chatId, String text,
        String label1, String callbackData1,
        String label2, String callbackData2);

    /**
     * Responde a un callback_query de Telegram (aparece como toast/popup al usuario).
     * Obligatorio llamarlo siempre que se recibe un callback.
     */
    void answerCallbackQuery(String callbackQueryId, String text);
}
