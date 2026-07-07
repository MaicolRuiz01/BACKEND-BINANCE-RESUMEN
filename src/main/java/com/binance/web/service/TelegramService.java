package com.binance.web.service;

public interface TelegramService {

    /** Envía un mensaje de texto simple a un chat. */
    Integer sendMessage(String chatId, String message);

    /**
     * Envía un mensaje con un botón inline al grupo.
     * @return message_id del mensaje enviado (para poder editarlo/borrarlo después).
     */
    Integer sendMessageWithButton(String chatId, String text, String buttonLabel, String callbackData);

    Integer sendMessageWithTwoButtons(String chatId, String text, String btn1Text, String btn1Data, String btn2Text, String btn2Data);

    /** Edita el texto de un mensaje ya enviado (para marcar la solicitud como tomada). */
    void editMessage(String chatId, Integer messageId, String newText);

    /** Edita el texto de un mensaje ya enviado y elimina sus botones. */
    void editMessageTextOnly(String chatId, Integer messageId, String newText);
    
    /** Edita el mensaje existente reemplazándolo con un teclado de botones dinámico. */
    void editMessageWithDynamicButtons(String chatId, Integer messageId, String text, java.util.Map<String, String> buttonsData);
    /** Elimina un mensaje de un chat. */
    void deleteMessage(String chatId, Integer messageId);

    /**
     * Responde a un callback_query de Telegram (aparece como toast/popup al usuario).
     * Obligatorio llamarlo siempre que se recibe un callback, o Telegram mostrará el spinner indefinidamente.
     */
    void answerCallbackQuery(String callbackQueryId, String text);
}
