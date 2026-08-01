package com.binance.web.conciliacion;

/**
 * Cuerpo del POST /conciliacion/registrar-chat — lo manda el bot de
 * conciliación con su chat_id de Telegram apenas alguien le escribe por
 * primera vez en un computador nuevo (ver _chat_id() en
 * conciliacion_bancaria.py). Idempotente: se puede volver a mandar sin
 * problema si el bot se reinicia o cambia de chat.
 */
public class RegistrarChatDto {

    private Long chatId;

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }
}
