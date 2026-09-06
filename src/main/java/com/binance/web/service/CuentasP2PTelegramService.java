package com.binance.web.service;

import java.util.List;
import java.util.Map;

/**
 * Cliente del bot de Telegram "Cuentas P2P" — token propio, separado del bot
 * de retiradores y del de conciliacion. Su trabajo principal es reenviar a
 * los chats de confianza lo que el sistema de Movimientos detecta en las
 * cuentas que se activaron desde "Cuentas P2P" en Pochonance (ver
 * MovimientosBridgeService).
 *
 * Desde 05/09/2026 también soporta el botón "✅ Liberada" en las notificaciones
 * de movimiento individual (a pedido de Milton, para no perder el hilo de qué
 * ventas ya se confirmaron cuando el chat se llena de transferencias) — ver
 * CuentasP2PTelegramWebhookService para el lado que recibe el click.
 */
public interface CuentasP2PTelegramService {

    /** Envía un mensaje de texto simple (Markdown) a un chat. */
    Integer sendMessage(String chatId, String message);

    /**
     * Igual que sendMessage, pero agrega un botón inline "✅" pegado al
     * mensaje (para marcar una venta/transferencia como liberada). Solo se
     * usa para eventos "movimiento" — el resto de eventos (cambio_saldo,
     * conexion_exitosa, error_login) siguen usando sendMessage normal.
     */
    Integer sendMessageConBotonLiberar(String chatId, String message);

    /**
     * Edita el mensaje para agregarle "✅ Liberada" al final y le quita el
     * botón (ya cumplió su propósito). Se le pasan las entities originales
     * del mensaje (formato bold/code de Telegram) tal cual, en vez de
     * parse_mode, para no arriesgarse a que un caracter especial en la
     * descripción del banco (ej. un guion bajo) rompa el parseo de Markdown
     * al reenviar el texto.
     */
    void marcarComoLiberado(String chatId, Integer messageId, String textoOriginal, List<Map<String, Object>> entities);

    /** Responde el callback del botón — Telegram lo exige para quitar el "cargando" del botón. */
    void answerCallbackQuery(String callbackQueryId);
}
