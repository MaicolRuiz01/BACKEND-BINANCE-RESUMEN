package com.binance.web.service;

import com.binance.web.Entity.*;
import com.binance.web.Repository.RetiradorRepository;
import com.binance.web.Repository.SolicitudRetiroRepository;
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
 * Maneja dos tipos:
 *   1. callback_query → alguien presionó un botón inline (accept:{solicitudId})
 *   2. message con /start → registra el chat_id privado del retirador
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramWebhookService {

    private final RetiradorRepository      retiradorRepository;
    private final SolicitudRetiroRepository solicitudRepository;
    private final TelegramService           telegramService;

    @Value("${app.telegram.group-chat-id:}")
    private String groupChatId;

    @Value("${app.telegram.group-invite-link:}")
    private String groupInviteLink;

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    // ─────────────────────────────────────────────────────────────────────────
    // Punto de entrada: procesar un update de Telegram
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void process(Map<String, Object> update) {
        if (update == null) return;

        // 1. Botón inline presionado
        if (update.containsKey("callback_query")) {
            Map<String, Object> callbackQuery = (Map<String, Object>) update.get("callback_query");
            handleCallbackQuery(callbackQuery);
            return;
        }

        // 2. Mensaje de texto (para /start)
        if (update.containsKey("message")) {
            Map<String, Object> message = (Map<String, Object>) update.get("message");
            handleMessage(message);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lógica: alguien presionó "✅ Aceptar"
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Transactional
    private void handleCallbackQuery(Map<String, Object> callbackQuery) {
        String callbackQueryId = (String) callbackQuery.get("id");
        String data            = (String) callbackQuery.get("data");

        // Solo procesamos callbacks con prefijo "accept:"
        if (data == null || !data.startsWith("accept:")) return;

        Map<String, Object> from    = (Map<String, Object>) callbackQuery.get("from");
        String telegramUsername      = (String) from.get("username");
        Long   telegramUserId        = toLong(from.get("id"));

        log.info("[Webhook] Callback '{}' de @{} (id={})", data, telegramUsername, telegramUserId);

        // Extraer solicitudId del callbackData: "accept:42" → 42
        long solicitudId;
        try {
            solicitudId = Long.parseLong(data.substring("accept:".length()));
        } catch (NumberFormatException e) {
            log.warn("[Webhook] callbackData inválido: {}", data);
            return;
        }

        // Buscar la solicitud
        Optional<SolicitudRetiro> optSolicitud = solicitudRepository.findById(solicitudId);
        if (optSolicitud.isEmpty()) {
            telegramService.answerCallbackQuery(callbackQueryId, "❌ Solicitud no encontrada.");
            return;
        }
        SolicitudRetiro solicitud = optSolicitud.get();

        // Verificar que aún esté SIN_ASIGNAR (race-condition guard)
        if (solicitud.getEstado() != EstadoSolicitud.SIN_ASIGNAR) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ Esta solicitud ya fue tomada por otro retirador.");
            return;
        }

        // Buscar al retirador por su username de Telegram
        if (telegramUsername == null || telegramUsername.isBlank()) {
            telegramService.answerCallbackQuery(callbackQueryId, "❌ Tu cuenta de Telegram no tiene @username configurado.");
            return;
        }

        Optional<Retirador> optRetirador = retiradorRepository.findByTelegramUsernameIgnoreCase(telegramUsername);
        if (optRetirador.isEmpty()) {
            telegramService.answerCallbackQuery(callbackQueryId,
                "❌ Tu @" + telegramUsername + " no está registrado en el sistema de retiradores.");
            return;
        }
        Retirador retirador = optRetirador.get();

        // Guardar chat_id privado del retirador si aún no está registrado
        if (retirador.getTelegramChatId() == null) {
            retirador.setTelegramChatId(telegramUserId);
            retiradorRepository.save(retirador);
        }

        // ── Asignar la solicitud ──────────────────────────────────────────
        solicitud.setRetirador(retirador);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        solicitudRepository.save(solicitud);

        log.info("[Webhook] Solicitud #{} asignada a {} (@{})", solicitudId, retirador.getNombre(), telegramUsername);

        // ── Respuesta al que presionó el botón ────────────────────────────
        telegramService.answerCallbackQuery(callbackQueryId,
            "✅ ¡Solicitud #" + solicitudId + " asignada a ti!");

        // ── Editar el mensaje del grupo para indicar que fue tomado ───────
        if (solicitud.getTelegramMessageId() != null && !groupChatId.isBlank()) {
            String textoEditado = buildTextoTomado(solicitud, retirador);
            telegramService.editMessage(groupChatId, solicitud.getTelegramMessageId(), textoEditado);
        }

        // ── Notificación privada al retirador (si tiene chat_id) ──────────
        if (retirador.getTelegramChatId() != null) {
            String mensajePrivado = buildMensajePrivado(solicitud, retirador);
            telegramService.sendMessage(String.valueOf(retirador.getTelegramChatId()), mensajePrivado);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lógica: alguien envió /start al bot → registrar su chat_id
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Transactional
    private void handleMessage(Map<String, Object> message) {
        String text = (String) message.get("text");
        if (text == null || !text.startsWith("/start")) return;

        Map<String, Object> from  = (Map<String, Object>) message.get("from");
        String telegramUsername    = (String) from.get("username");
        Long   chatId              = toLong(from.get("id"));

        if (telegramUsername == null || chatId == null) return;

        retiradorRepository.findByTelegramUsernameIgnoreCase(telegramUsername).ifPresent(retirador -> {
            retirador.setTelegramChatId(chatId);
            retiradorRepository.save(retirador);
            log.info("[Webhook] Chat ID {} registrado para @{} ({})", chatId, telegramUsername, retirador.getNombre());
            
            String msg = "✅ ¡Hola *" + retirador.getNombre() + "*! Tu cuenta queda registrada para recibir notificaciones de retiro directamente.";
            if (groupInviteLink != null && !groupInviteLink.isBlank()) {
                msg += "\n\n👉 *Únete al grupo de retiradores aquí:* [Enlace al Grupo](" + groupInviteLink + ")";
            }
            
            telegramService.sendMessage(String.valueOf(chatId), msg);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers de texto
    // ─────────────────────────────────────────────────────────────────────────

    private String buildTextoTomado(SolicitudRetiro solicitud, Retirador retirador) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ *Solicitud de Retiro #").append(solicitud.getId()).append(" — TOMADA*\n");
        sb.append("👤 *Asignada a:* ").append(retirador.getNombre());
        if (retirador.getTelegramUsername() != null) {
            sb.append(" (@").append(retirador.getTelegramUsername()).append(")");
        }
        sb.append("\n💰 *Monto:* $").append(String.format("%,.0f", solicitud.getTotalMonto())).append(" COP");
        return sb.toString();
    }

    private String buildMensajePrivado(SolicitudRetiro solicitud, Retirador retirador) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 *¡Solicitud de Retiro #").append(solicitud.getId()).append(" asignada a ti!*\n\n");
        sb.append("💰 *Monto Total:* $").append(String.format("%,.0f", solicitud.getTotalMonto())).append(" COP\n");
        sb.append("💵 *Tu pago:* $").append(String.format("%,.0f", solicitud.getPagoRetirador())).append(" COP\n");
        sb.append("🏦 *Detalles:*");
        for (var d : solicitud.getDetalles()) {
            sb.append("\n  • ").append(d.getCuentaCop().getName())
              .append(" (").append(d.getCuentaCop().getBankType().name()).append(")")
              .append(" | ").append(d.getTipoRetiro().name())
              .append(" | Cajero: $").append(String.format("%,.0f", d.getMontoCajero()))
              .append(" | Corresponsal: $").append(String.format("%,.0f", d.getMontoCorresponsal()));
        }
        sb.append("\n\n⏰ *Confirma el retiro desde la app una vez completado.*");
        return sb.toString();
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Long l) return l;
        if (o instanceof Integer i) return i.longValue();
        return null;
    }
}
