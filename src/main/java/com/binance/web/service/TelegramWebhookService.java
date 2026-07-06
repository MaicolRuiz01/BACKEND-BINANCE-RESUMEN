package com.binance.web.service;

import com.binance.web.Entity.*;
import com.binance.web.Repository.RetiradorRepository;
import com.binance.web.Repository.SolicitudRetiroRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.movimientos.MovimientoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final RetiradorRepository       retiradorRepository;
    private final SolicitudRetiroRepository solicitudRepository;
    private final TelegramService           telegramService;
    private final RetiradorService          retiradorService;
    private final SupplierRepository        supplierRepository;
    private final MovimientoService         movimientoService;

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
    // Lógica: alguien presionó un botón inline
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Transactional
    private void handleCallbackQuery(Map<String, Object> callbackQuery) {
        String callbackQueryId = (String) callbackQuery.get("id");
        String data            = (String) callbackQuery.get("data");

        if (data == null) return;

        Map<String, Object> from    = (Map<String, Object>) callbackQuery.get("from");
        String telegramUsername      = (String) from.get("username");
        Long   telegramUserId        = toLong(from.get("id"));

        Map<String, Object> message = (Map<String, Object>) callbackQuery.get("message");
        Integer messageId = message != null ? (Integer) message.get("message_id") : null;

        log.info("[Webhook] Callback '{}' de @{} (id={})", data, telegramUsername, telegramUserId);

        if (data.startsWith("accept:")) {
            handleAccept(callbackQueryId, data, telegramUsername, telegramUserId);
        } else if (data.startsWith("completed:")) {
            handleCompleted(callbackQueryId, data, telegramUserId, messageId);
        } else if (data.startsWith("cancel:")) {
            handleCancel(callbackQueryId, data, telegramUserId, messageId);
        } else if (data.equals("entregar_start")) {
            handleEntregarStart(callbackQueryId, telegramUserId, messageId);
        } else if (data.startsWith("entregar_prov:")) {
            handleEntregarProv(callbackQueryId, data, telegramUserId, messageId);
        }
    }

    private void handleEntregarStart(String callbackQueryId, Long telegramUserId, Integer messageId) {
        // Encontrar retirador
        Retirador retirador = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        if (retirador == null) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ No estás registrado como retirador.");
            return;
        }

        if (retirador.getEfectivo() == null || retirador.getEfectivo().getSaldo() <= 0) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ Tu caja ya está en cero.");
            return;
        }

        // Obtener proveedores
        java.util.List<Supplier> proveedores = supplierRepository.findAll();
        if (proveedores.isEmpty()) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ No hay proveedores configurados.");
            return;
        }

        // Construir mapa de botones (Nombre -> callback_data)
        java.util.LinkedHashMap<String, String> buttonsData = new java.util.LinkedHashMap<>();
        for (Supplier prov : proveedores) {
            buttonsData.put(prov.getName(), "entregar_prov:" + prov.getId());
        }

        telegramService.editMessageWithDynamicButtons(
                String.valueOf(telegramUserId),
                messageId,
                "🏦 *Selecciona el proveedor al que le vas a entregar tu efectivo:*",
                buttonsData
        );
        telegramService.answerCallbackQuery(callbackQueryId, "");
    }

    private void handleEntregarProv(String callbackQueryId, String data, Long telegramUserId, Integer messageId) {
        Integer proveedorId;
        try {
            proveedorId = Integer.parseInt(data.substring("entregar_prov:".length()));
        } catch (NumberFormatException e) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ Error en los datos del proveedor.");
            return;
        }

        Retirador retirador = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        if (retirador == null || retirador.getEfectivo() == null) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ No se encontró tu caja.");
            return;
        }

        Double montoCaja = retirador.getEfectivo().getSaldo();
        if (montoCaja <= 0) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ Tu caja ya está en cero.");
            // Restaurar mensaje a sin botones para limpiar
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), messageId, "Tu caja está en $0.");
            return;
        }

        Supplier proveedor = supplierRepository.findById(proveedorId).orElse(null);
        if (proveedor == null) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ El proveedor ya no existe.");
            return;
        }

        try {
            // Registrar pago al proveedor desde la caja del retirador
            movimientoService.registrarPagoProveedor(null, retirador.getEfectivo().getId(), null, proveedor.getId(), null, montoCaja);
            
            // Si el servicio no arroja error, la transacción fue exitosa
            String mensajeExito = String.format("✅ *Entregado con éxito*\n\nHas entregado *$%,.0f* a *%s*.\nTu caja ahora está en *$0*.", montoCaja, proveedor.getName());
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), messageId, mensajeExito);
            telegramService.answerCallbackQuery(callbackQueryId, "Dinero entregado exitosamente.");
        } catch (Exception e) {
            log.error("[Webhook] Error entregando a proveedor", e);
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ Hubo un error al registrar el pago.");
        }
    }

    private void handleAccept(String callbackQueryId, String data, String telegramUsername, Long telegramUserId) {
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

        Optional<Retirador> optRetirador = findRetiradorByUsername(telegramUsername);
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

        // ── Notificación privada al retirador con los 2 botones ──────────
        if (retirador.getTelegramChatId() != null) {
            String mensajePrivado = buildMensajePrivado(solicitud, retirador);
            telegramService.sendMessageWithTwoButtons(
                String.valueOf(retirador.getTelegramChatId()),
                mensajePrivado,
                "✅ Ya hice el retiro", "completed:" + solicitud.getId(),
                "❌ Cancelar", "cancel:" + solicitud.getId()
            );
        }
    }

    private void handleCompleted(String callbackQueryId, String data, Long telegramUserId, Integer messageId) {
        long solicitudId = Long.parseLong(data.substring("completed:".length()));
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId).orElse(null);

        if (solicitud == null || solicitud.getEstado() == EstadoSolicitud.COMPLETADO) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ Esta solicitud ya está completada o no existe.");
            return;
        }

        // Confirmar la solicitud (esto actualiza balances)
        try {
            retiradorService.confirmarSolicitud(solicitudId);
        } catch (IllegalStateException e) {
            // Saldo insuficiente u otra validación de negocio: avisar y no continuar
            telegramService.answerCallbackQuery(callbackQueryId, "❌ " + e.getMessage());
            return;
        }

        // Obtener el retirador actualizado (con su caja)
        Retirador retirador = retiradorRepository.findById(solicitud.getRetirador().getId()).orElse(null);

        telegramService.answerCallbackQuery(callbackQueryId, "✅ Retiro marcado como completado.");

        // Editar el mensaje original para quitar los botones
        if (messageId != null) {
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), messageId, 
                "✅ *Retiro completado* (Solicitud #" + solicitudId + ")");
        }

        // Enviar el mensaje de la caja
        if (retirador != null && retirador.getEfectivo() != null) {
            Efectivo caja = retirador.getEfectivo();
            String nombreCaja = caja.getName() != null ? caja.getName() : ("Caja " + retirador.getNombre());
            String msjCaja = "💰 *Saldo en tu caja*\n\n" +
                             nombreCaja + ": $" + String.format("%,.0f", caja.getSaldo()) + "\n\n" +
                             "No olvides entregar el efectivo.";
            telegramService.sendMessage(String.valueOf(telegramUserId), msjCaja);

            // Disparar de inmediato el recordatorio con botón "Entregar efectivo",
            // en vez de esperar al ciclo programado (cada 30 min)
            retiradorService.enviarRecordatorioCaja(retirador);
        }
    }

    private void handleCancel(String callbackQueryId, String data, Long telegramUserId, Integer messageId) {
        long solicitudId = Long.parseLong(data.substring("cancel:".length()));
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId).orElse(null);

        if (solicitud == null || solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ La solicitud no se puede cancelar en este estado.");
            return;
        }

        // Regresar a SIN_ASIGNAR
        solicitud.setRetirador(null);
        solicitud.setEstado(EstadoSolicitud.SIN_ASIGNAR);
        solicitudRepository.save(solicitud);

        // Reenviar mensaje al grupo
        retiradorService.reenviarSolicitudGeneral(solicitud);

        telegramService.answerCallbackQuery(callbackQueryId, "❌ Has cancelado la solicitud.");

        // Editar el mensaje original para quitar los botones
        if (messageId != null) {
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), messageId, 
                "❌ *Retiro cancelado* (Solicitud #" + solicitudId + " ha vuelto al grupo)");
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

        findRetiradorByUsername(telegramUsername).ifPresent(retirador -> {
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

    private Optional<Retirador> findRetiradorByUsername(String telegramUsername) {
        if (telegramUsername == null) return Optional.empty();
        String cleanUsername = telegramUsername.startsWith("@") ? telegramUsername.substring(1) : telegramUsername;
        
        Optional<Retirador> opt = retiradorRepository.findByTelegramUsernameIgnoreCase(cleanUsername);
        if (opt.isPresent()) return opt;
        
        return retiradorRepository.findByTelegramUsernameIgnoreCase("@" + cleanUsername);
    }
}
