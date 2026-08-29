package com.binance.web.service;

/**
 * Cliente del bot de Telegram "Cuentas P2P" — token propio, separado del bot
 * de retiradores y del de conciliacion. Su unico trabajo es reenviar a los
 * chats de confianza lo que el sistema de Movimientos detecta en las cuentas
 * que se activaron desde "Cuentas P2P" en Pochonance (ver
 * MovimientosBridgeService). No maneja botones ni recibe updates — es un
 * canal de solo salida.
 */
public interface CuentasP2PTelegramService {

    /** Envía un mensaje de texto simple (Markdown) a un chat. */
    Integer sendMessage(String chatId, String message);
}
