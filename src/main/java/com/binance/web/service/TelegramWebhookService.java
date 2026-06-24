package com.binance.web.service;

import com.binance.web.Entity.*;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.Repository.RetiradorRepository;
import com.binance.web.Repository.SolicitudRetiroRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.config.TelegramConversationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * Procesa los updates recibidos desde Telegram vía webhook.
 *
 * Callbacks manejados:
 *   accept:{id}      → retirador acepta solicitud del grupo
 *   confirm:{id}     → retirador confirma retiro físico
 *   cancel:{id}      → retirador cancela y devuelve la solicitud al grupo
 *   entrega_todo     → retirador entrega todo el saldo de su caja al proveedor
 *
 * Mensajes de texto manejados:
 *   /start   → registra el chat_id privado del retirador
 *   entrega  → inicia flujo de entrega parcial o total de efectivo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramWebhookService {

    private final RetiradorRepository       retiradorRepository;
    private final SolicitudRetiroRepository  solicitudRepository;
    private final EfectivoRepository         efectivoRepository;
    private final SupplierRepository         supplierRepository;
    private final MovimientoRepository       movimientoRepository;
    private final TelegramService            telegramService;
    private final RetiradorService           retiradorService;
    private final TelegramConversationState  conversationState;

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Value("${app.telegram.group-chat-id:}")
    private String groupChatId;

    // ─────────────────────────────────────────────────────────────────────────
    // Punto de entrada
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    @SuppressWarnings("unchecked")
    public void process(Map<String, Object> update) {
        if (update == null) return;

        if (update.containsKey("callback_query")) {
            handleCallbackQuery((Map<String, Object>) update.get("callback_query"));
            return;
        }
        if (update.containsKey("message")) {
            handleMessage((Map<String, Object>) update.get("message"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Router de callbacks
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleCallbackQuery(Map<String, Object> callbackQuery) {
        String callbackQueryId = (String) callbackQuery.get("id");
        String data            = (String) callbackQuery.get("data");
        if (data == null) return;

        Map<String, Object> from = (Map<String, Object>) callbackQuery.get("from");
        String username          = (String) from.get("username");
        Long   userId            = toLong(from.get("id"));

        Map<String, Object> btnMsg = (Map<String, Object>) callbackQuery.get("message");
        Integer btnMessageId       = btnMsg != null ? (Integer) btnMsg.get("message_id") : null;
        Long    btnChatId          = null;
        if (btnMsg != null) {
            Map<String, Object> chat = (Map<String, Object>) btnMsg.get("chat");
            if (chat != null) btnChatId = toLong(chat.get("id"));
        }

        log.info("[Webhook] Callback '{}' de @{} (id={})", data, username, userId);

        if (data.startsWith("accept:")) {
            handleAccept(callbackQueryId, data, username, userId);
        } else if (data.startsWith("confirm:")) {
            handleConfirm(callbackQueryId, data, userId, btnMessageId, btnChatId);
        } else if (data.startsWith("cancel:")) {
            handleCancel(callbackQueryId, data, userId, btnMessageId, btnChatId);
        } else if (data.equals("entrega_todo")) {
            handleEntregaTodo(callbackQueryId, userId, btnMessageId, btnChatId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // accept:{solicitudId}
    // ─────────────────────────────────────────────────────────────────────────

    private void handleAccept(String callbackQueryId, String data,
                              String telegramUsername, Long telegramUserId) {
        long solicitudId;
        try {
            solicitudId = Long.parseLong(data.substring("accept:".length()));
        } catch (NumberFormatException e) {
            log.warn("[Webhook] callbackData inválido: {}", data);
            return;
        }

        Optional<SolicitudRetiro> optSolicitud = solicitudRepository.findById(solicitudId);
        if (optSolicitud.isEmpty()) {
            telegramService.answerCallbackQuery(callbackQueryId, "❌ Solicitud no encontrada.");
            return;
        }
        SolicitudRetiro solicitud = optSolicitud.get();

        if (solicitud.getEstado() != EstadoSolicitud.SIN_ASIGNAR) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ Esta solicitud ya fue tomada.");
            return;
        }

        if (telegramUsername == null || telegramUsername.isBlank()) {
            telegramService.answerCallbackQuery(callbackQueryId,
                "❌ Tu cuenta no tiene @username configurado en Telegram.");
            return;
        }

        Optional<Retirador> optRetirador = findRetiradorByUsername(telegramUsername);
        if (optRetirador.isEmpty()) {
            telegramService.answerCallbackQuery(callbackQueryId,
                "❌ @" + telegramUsername + " no está registrado como retirador.");
            return;
        }
        Retirador retirador = optRetirador.get();

        retirador.setTelegramChatId(telegramUserId);
        retiradorRepository.save(retirador);

        solicitud.setRetirador(retirador);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        solicitud.setFechaAsignacion(LocalDateTime.now(BOGOTA));
        solicitudRepository.save(solicitud);

        log.info("[Webhook] Solicitud #{} asignada a {} (@{})", solicitudId, retirador.getNombre(), telegramUsername);

        telegramService.answerCallbackQuery(callbackQueryId,
            "✅ ¡Retiro #" + solicitudId + " asignado a ti! Revisa tus mensajes privados.");

        if (solicitud.getTelegramMessageId() != null && !groupChatId.isBlank()) {
            telegramService.editMessage(groupChatId, solicitud.getTelegramMessageId(),
                buildTextoTomado(solicitud, retirador));
        }

        telegramService.sendMessageWithTwoButtons(
            String.valueOf(telegramUserId),
            buildMensajePrivado(solicitud),
            "✅ Ya hice el retiro", "confirm:" + solicitudId,
            "❌ Cancelar", "cancel:" + solicitudId
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // confirm:{solicitudId}
    // ─────────────────────────────────────────────────────────────────────────

    private void handleConfirm(String callbackQueryId, String data,
                               Long telegramUserId, Integer btnMessageId, Long btnChatId) {
        long solicitudId;
        try {
            solicitudId = Long.parseLong(data.substring("confirm:".length()));
        } catch (NumberFormatException e) {
            log.warn("[Webhook] callbackData inválido: {}", data);
            return;
        }

        Optional<SolicitudRetiro> optSolicitud = solicitudRepository.findById(solicitudId);
        if (optSolicitud.isEmpty()) {
            telegramService.answerCallbackQuery(callbackQueryId, "❌ Solicitud no encontrada.");
            return;
        }
        SolicitudRetiro solicitud = optSolicitud.get();

        if (solicitud.getEstado() == EstadoSolicitud.COMPLETADO) {
            telegramService.answerCallbackQuery(callbackQueryId, "ℹ️ Este retiro ya fue confirmado anteriormente.");
            return;
        }
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ La solicitud no está en estado PENDIENTE.");
            return;
        }

        Retirador retirador = solicitud.getRetirador();
        if (retirador == null || !telegramUserId.equals(retirador.getTelegramChatId())) {
            telegramService.answerCallbackQuery(callbackQueryId, "❌ Solo el retirador asignado puede confirmar este retiro.");
            return;
        }

        try {
            retiradorService.confirmarSolicitud(solicitudId);
        } catch (Exception e) {
            log.error("[Webhook] Error al confirmar solicitud #{}: {}", solicitudId, e.getMessage());
            telegramService.answerCallbackQuery(callbackQueryId, "❌ Error al confirmar. Intenta desde la app.");
            return;
        }

        log.info("[Webhook] Solicitud #{} confirmada por {} (chatId={})",
            solicitudId, retirador.getNombre(), telegramUserId);

        if (solicitud.getReminderMessageId() != null) {
            telegramService.deleteMessage(String.valueOf(telegramUserId), solicitud.getReminderMessageId());
            solicitud.setReminderMessageId(null);
            solicitud.setUltimoRecordatorio(null);
            solicitudRepository.save(solicitud);
        }

        telegramService.answerCallbackQuery(callbackQueryId, "✅ ¡Retiro confirmado!");

        if (btnMessageId != null && btnChatId != null) {
            telegramService.editMessage(String.valueOf(btnChatId), btnMessageId,
                "✅ *Retiro #" + solicitudId + " confirmado*\n\n"
                + "Monto: $" + fmt(solicitud.getTotalMonto()) + "\n\n"
                + "El dinero ya está registrado en tu caja.");
        }

        retiradorRepository.findById(retirador.getId()).ifPresent(r -> {
            if (r.getEfectivo() != null) {
                telegramService.sendMessage(String.valueOf(telegramUserId),
                    "💰 *Saldo en tu caja*\n\n"
                    + r.getEfectivo().getName() + ": *$" + fmt(r.getEfectivo().getSaldo()) + "*\n\n"
                    + "No olvides entregar el efectivo.");
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // cancel:{solicitudId}
    // ─────────────────────────────────────────────────────────────────────────

    private void handleCancel(String callbackQueryId, String data,
                              Long telegramUserId, Integer btnMessageId, Long btnChatId) {
        long solicitudId;
        try {
            solicitudId = Long.parseLong(data.substring("cancel:".length()));
        } catch (NumberFormatException e) {
            log.warn("[Webhook] callbackData inválido: {}", data);
            return;
        }

        Optional<SolicitudRetiro> optSolicitud = solicitudRepository.findById(solicitudId);
        if (optSolicitud.isEmpty()) {
            telegramService.answerCallbackQuery(callbackQueryId, "❌ Solicitud no encontrada.");
            return;
        }
        SolicitudRetiro solicitud = optSolicitud.get();

        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ Este retiro ya no puede cancelarse.");
            return;
        }

        Retirador retirador = solicitud.getRetirador();
        if (retirador == null || !telegramUserId.equals(retirador.getTelegramChatId())) {
            telegramService.answerCallbackQuery(callbackQueryId, "❌ Solo el retirador asignado puede cancelar.");
            return;
        }

        if (solicitud.getReminderMessageId() != null) {
            telegramService.deleteMessage(String.valueOf(telegramUserId), solicitud.getReminderMessageId());
        }

        solicitud.setRetirador(null);
        solicitud.setEstado(EstadoSolicitud.SIN_ASIGNAR);
        solicitud.setTelegramMessageId(null);
        solicitud.setFechaAsignacion(null);
        solicitud.setUltimoRecordatorio(null);
        solicitud.setReminderMessageId(null);
        solicitudRepository.save(solicitud);

        log.info("[Webhook] Solicitud #{} cancelada por {} (chatId={})",
            solicitudId, retirador.getNombre(), telegramUserId);

        telegramService.answerCallbackQuery(callbackQueryId, "↩️ Cancelado. El retiro vuelve al grupo.");

        if (btnMessageId != null && btnChatId != null) {
            telegramService.editMessage(String.valueOf(btnChatId), btnMessageId,
                "↩️ *Retiro #" + solicitudId + " cancelado*\n\nEl retiro ha vuelto al grupo.");
        }

        if (!groupChatId.isBlank()) {
            Integer newMsgId = telegramService.sendMessageWithButton(
                groupChatId, buildMensajeGrupo(solicitud), "✅ Aceptar", "accept:" + solicitudId
            );
            if (newMsgId != null) {
                solicitud.setTelegramMessageId(newMsgId);
                solicitudRepository.save(solicitud);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // entrega_todo — retirador liquida todo su saldo de caja al proveedor
    // ─────────────────────────────────────────────────────────────────────────

    private void handleEntregaTodo(String callbackQueryId, Long chatId,
                                   Integer btnMessageId, Long btnChatId) {
        conversationState.clear(chatId); // cancelar estado de espera si estaba activo

        Optional<Retirador> optRetirador = retiradorRepository.findByTelegramChatId(chatId);
        if (optRetirador.isEmpty()) {
            telegramService.answerCallbackQuery(callbackQueryId, "❌ No estás registrado como retirador.");
            return;
        }
        Retirador retirador = optRetirador.get();

        Efectivo caja = retirador.getEfectivo();
        if (caja == null || caja.getSaldo() <= 0.0) {
            telegramService.answerCallbackQuery(callbackQueryId, "❌ Tu caja ya está en cero.");
            return;
        }

        double totalUnits = caja.getSaldo();

        // Editar el mensaje del prompt para que desaparezca el botón
        if (btnMessageId != null && btnChatId != null) {
            telegramService.editMessage(String.valueOf(btnChatId), btnMessageId,
                "💵 Registrando entrega total de *$" + fmt(totalUnits) + "*…");
        }

        registrarEntregaProveedor(retirador, totalUnits, chatId);
        telegramService.answerCallbackQuery(callbackQueryId, "✅ ¡Entrega total registrada!");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mensajes de texto
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void handleMessage(Map<String, Object> message) {
        String text = (String) message.get("text");
        if (text == null) return;

        Map<String, Object> from = (Map<String, Object>) message.get("from");
        if (from == null) return;
        Long   chatId           = toLong(from.get("id"));
        String telegramUsername = (String) from.get("username");

        if (chatId == null) return;

        // /start → registrar chatId
        if (text.startsWith("/start")) {
            if (telegramUsername != null) handleStart(chatId, telegramUsername);
            return;
        }

        // Esperando monto de entrega
        if (conversationState.is(chatId, "WAITING_ENTREGA_AMOUNT")) {
            conversationState.clear(chatId);
            processEntregaParcial(chatId, text.trim());
            return;
        }

        // "entrega" → preguntar cuánto y ofrecer botón "Todo"
        if (text.trim().equalsIgnoreCase("entrega")) {
            conversationState.set(chatId, "WAITING_ENTREGA_AMOUNT");
            Optional<Retirador> optR = retiradorRepository.findByTelegramChatId(chatId);
            if (optR.isPresent() && optR.get().getEfectivo() != null) {
                double saldo = optR.get().getEfectivo().getSaldo();
                String prompt = "💵 ¿Cuánto dinero vas a entregar? Escribe el monto en pesos."
                    + "\n\nTienes: *$" + fmt(saldo) + "*";
                telegramService.sendMessageWithButton(
                    String.valueOf(chatId), prompt,
                    "💰 Entregar todo ($" + fmt(saldo) + ")", "entrega_todo"
                );
