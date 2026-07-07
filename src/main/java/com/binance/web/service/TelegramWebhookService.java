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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Procesa los updates recibidos desde Telegram vía webhook.
 * Maneja dos tipos:
 * 1. callback_query → alguien presionó un botón inline (accept:{solicitudId})
 * 2. message con /start → registra el chat_id privado del retirador
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramWebhookService {

    private final RetiradorRepository retiradorRepository;
    private final SolicitudRetiroRepository solicitudRepository;
    private final TelegramService telegramService;
    private final RetiradorService retiradorService;
    private final SupplierRepository supplierRepository;
    private final MovimientoService movimientoService;
    private final GastoService gastoService;

    @Value("${app.telegram.group-chat-id:}")
    private String groupChatId;

    @Value("${app.telegram.group-invite-link:}")
    private String groupInviteLink;

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    // Estado en memoria: retiradores que están en medio del flujo "Entregar
    // efectivo" y ya eligieron proveedor, esperando que escriban el monto.
    // Key = telegramUserId (chat_id privado).
    private final Map<Long, PendingEntrega> pendingEntregas = new ConcurrentHashMap<>();

    private record PendingEntrega(Integer proveedorId, String proveedorNombre, Integer messageId) {
    }

    // Estado en memoria: retiradores que están en medio del flujo "Registrar
    // gasto", esperando que escriban el monto y la descripción.
    // Key = telegramUserId (chat_id privado).
    private final Map<Long, PendingGasto> pendingGastos = new ConcurrentHashMap<>();

    private record PendingGasto(Integer messageId) {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Punto de entrada: procesar un update de Telegram
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void process(Map<String, Object> update) {
        if (update == null)
            return;

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
        String data = (String) callbackQuery.get("data");

        if (data == null)
            return;

        Map<String, Object> from = (Map<String, Object>) callbackQuery.get("from");
        String telegramUsername = (String) from.get("username");
        Long telegramUserId = toLong(from.get("id"));

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
        } else if (data.equals("entregar_cancel")) {
            handleEntregarCancel(callbackQueryId, telegramUserId, messageId);
        } else if (data.equals("gasto_start")) {
            handleGastoStart(callbackQueryId, telegramUserId, messageId);
        } else if (data.equals("gasto_cancel")) {
            handleGastoCancel(callbackQueryId, telegramUserId, messageId);
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

        // Descartar cualquier flujo de entrega/gasto que hubiera quedado a medias
        pendingEntregas.remove(telegramUserId);
        pendingGastos.remove(telegramUserId);

        // Construir mapa de botones (Nombre -> callback_data)
        java.util.LinkedHashMap<String, String> buttonsData = new java.util.LinkedHashMap<>();
        for (Supplier prov : proveedores) {
            buttonsData.put(prov.getName(), "entregar_prov:" + prov.getId());
        }

        telegramService.editMessageWithDynamicButtons(
                String.valueOf(telegramUserId),
                messageId,
                "🏦 *Selecciona el proveedor al que le vas a entregar tu efectivo:*",
                buttonsData);
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

        Double saldoDisponible = retirador.getEfectivo().getSaldo();
        if (saldoDisponible <= 0) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ Tu caja ya está en cero.");
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), messageId, "Tu caja está en $0.");
            return;
        }

        Supplier proveedor = supplierRepository.findById(proveedorId).orElse(null);
        if (proveedor == null) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ El proveedor ya no existe.");
            return;
        }

        // No entregamos de inmediato: guardamos el estado y pedimos el monto,
        // porque el retirador puede querer entregar solo una parte de la caja.
        pendingEntregas.put(telegramUserId, new PendingEntrega(proveedor.getId(), proveedor.getName(), messageId));

        String texto = String.format(
                "💵 Vas a entregarle a *%s*.\n\nTu caja tiene *$%,.0f* disponibles.\n\n" +
                        "Escribe el monto a entregar, o escribe *todo* para entregar el saldo completo.",
                proveedor.getName(), saldoDisponible);

        java.util.LinkedHashMap<String, String> buttonsData = new java.util.LinkedHashMap<>();
        buttonsData.put("❌ Cancelar", "entregar_cancel");
        telegramService.editMessageWithDynamicButtons(String.valueOf(telegramUserId), messageId, texto, buttonsData);
        telegramService.answerCallbackQuery(callbackQueryId, "");
    }

    private void handleEntregarCancel(String callbackQueryId, Long telegramUserId, Integer messageId) {
        pendingEntregas.remove(telegramUserId);

        Retirador retirador = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        if (retirador == null || retirador.getEfectivo() == null || retirador.getEfectivo().getSaldo() <= 0) {
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), messageId, "Entrega cancelada.");
            telegramService.answerCallbackQuery(callbackQueryId, "");
            return;
        }

        restaurarRecordatorio(telegramUserId, messageId, retirador);
        // Sin popup: el mensaje ya se restauró mostrando el estado actual de la caja.
        telegramService.answerCallbackQuery(callbackQueryId, "");
    }

    private void handleGastoStart(String callbackQueryId, Long telegramUserId, Integer messageId) {
        Retirador retirador = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        if (retirador == null) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ No estás registrado como retirador.");
            return;
        }
        if (retirador.getEfectivo() == null || retirador.getEfectivo().getSaldo() <= 0) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ Tu caja ya está en cero.");
            return;
        }

        // Descartar cualquier flujo de entrega que hubiera quedado a medias
        pendingEntregas.remove(telegramUserId);
        pendingGastos.put(telegramUserId, new PendingGasto(messageId));

        String texto = String.format(
                "🧾 *Registrar gasto*\n\nTu caja tiene *$%,.0f* disponibles.\n\n" +
                        "Escribe el monto y una breve descripción, separados por un espacio.",
                retirador.getEfectivo().getSaldo());

        java.util.LinkedHashMap<String, String> buttonsData = new java.util.LinkedHashMap<>();
        buttonsData.put("❌ Cancelar", "gasto_cancel");
        telegramService.editMessageWithDynamicButtons(String.valueOf(telegramUserId), messageId, texto, buttonsData);
        telegramService.answerCallbackQuery(callbackQueryId, "");
    }

    private void handleGastoCancel(String callbackQueryId, Long telegramUserId, Integer messageId) {
        pendingGastos.remove(telegramUserId);

        Retirador retirador = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        if (retirador == null || retirador.getEfectivo() == null || retirador.getEfectivo().getSaldo() <= 0) {
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), messageId,
                    "Registro de gasto cancelado.");
            telegramService.answerCallbackQuery(callbackQueryId, "");
            return;
        }

        restaurarRecordatorio(telegramUserId, messageId, retirador);
        // Sin popup: el mensaje ya se restauró mostrando el estado actual de la caja.
        telegramService.answerCallbackQuery(callbackQueryId, "");
    }

    /**
     * Procesa el texto que el retirador escribió como respuesta al flujo
     * "Registrar gasto". Espera el formato "<monto> <descripción>", ej: "2000
     * agua".
     */
    private void handleGastoMonto(PendingGasto pending, Long telegramUserId, String textoRecibido) {
        Retirador retirador = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        if (retirador == null || retirador.getEfectivo() == null) {
            pendingGastos.remove(telegramUserId);
            telegramService.sendMessage(String.valueOf(telegramUserId), "⚠️ No se encontró tu caja.");
            return;
        }

        double saldoActual = retirador.getEfectivo().getSaldo();
        if (saldoActual <= 0) {
            pendingGastos.remove(telegramUserId);
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), pending.messageId(),
                    "Tu caja está en $0.");
            return;
        }

        String limpio = textoRecibido.trim();
        String[] partes = limpio.split("\\s+", 2);
        String montoTexto = partes[0];
        String descripcion = partes.length > 1 ? partes[1].trim() : "";

        if (descripcion.isBlank()) {
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ Escribe el monto y una descripción, ej: `2000 agua`.");
            return;
        }

        String soloNumeros = montoTexto.replace("$", "").replace(".", "").replace(",", "");
        Double monto;
        try {
            monto = Double.parseDouble(soloNumeros);
        } catch (NumberFormatException e) {
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ No entendí ese monto. Escribe primero el número y luego la descripción, ej: `2000 agua`.");
            return;
        }

        if (monto <= 0) {
            telegramService.sendMessage(String.valueOf(telegramUserId), "⚠️ El monto debe ser mayor a $0.");
            return;
        }
        if (monto > saldoActual) {
            telegramService.sendMessage(String.valueOf(telegramUserId), String.format(
                    "⚠️ Ese gasto supera lo que tienes en caja (*$%,.0f*). Escribe un monto menor o igual.",
                    saldoActual));
            return;
        }

        try {
            Gasto gasto = new Gasto();
            gasto.setDescripcion(descripcion);
            gasto.setMonto(monto);
            gasto.setPagoEfectivo(retirador.getEfectivo());
            gasto.setIdempotencyKey("telegram-gasto-" + telegramUserId + "-" + System.currentTimeMillis());
            gastoService.saveGasto(gasto);
        } catch (Exception e) {
            log.error("[Webhook] Error registrando gasto", e);
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ Hubo un error al registrar el gasto. Intenta de nuevo.");
            return;
        }

        pendingGastos.remove(telegramUserId);
        double restante = saldoActual - monto;

        if (restante > 0) {
            String textoFinal = String.format(
                    "✅ Gasto registrado: *$%,.0f* — %s.\n\n💰 Caja: *$%,.0f*",
                    monto, descripcion, restante);
            java.util.LinkedHashMap<String, String> buttonsData = new java.util.LinkedHashMap<>();
            buttonsData.put("✅ Entregar efectivo", "entregar_start");
            buttonsData.put("🧾 Registrar gasto", "gasto_start");
            telegramService.editMessageWithDynamicButtons(String.valueOf(telegramUserId), pending.messageId(),
                    textoFinal, buttonsData);
        } else {
            String textoFinal = String.format(
                    "✅ Gasto registrado: *$%,.0f* — %s.\n\nTu caja ahora está en *$0*.",
                    monto, descripcion);
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), pending.messageId(), textoFinal);
        }

        log.info("[Gasto] {} registró un gasto de ${} ({}) — restante ${}",
                retirador.getNombre(), monto, descripcion, restante);
    }

    /**
     * Reconstruye el mensaje de recordatorio de caja con sus dos botones
     * habituales.
     */
    private void restaurarRecordatorio(Long telegramUserId, Integer messageId, Retirador retirador) {
        String texto = String.format("💰 Caja: *$%,.0f*", retirador.getEfectivo().getSaldo());
        java.util.LinkedHashMap<String, String> buttonsData = new java.util.LinkedHashMap<>();
        buttonsData.put("✅ Entregar efectivo", "entregar_start");
        buttonsData.put("🧾 Registrar gasto", "gasto_start");
        telegramService.editMessageWithDynamicButtons(String.valueOf(telegramUserId), messageId, texto, buttonsData);
    }

    /**
     * Procesa el monto que el retirador escribió como respuesta al flujo
     * "Entregar efectivo". Acepta números (con o sin $, puntos o comas) o la
     * palabra "todo" para entregar el saldo completo.
     */
    private void handleEntregarMonto(PendingEntrega pending, Long telegramUserId, String textoRecibido) {
        Retirador retirador = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        if (retirador == null || retirador.getEfectivo() == null) {
            pendingEntregas.remove(telegramUserId);
            telegramService.sendMessage(String.valueOf(telegramUserId), "⚠️ No se encontró tu caja.");
            return;
        }

        double saldoActual = retirador.getEfectivo().getSaldo();
        if (saldoActual <= 0) {
            pendingEntregas.remove(telegramUserId);
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), pending.messageId(),
                    "Tu caja está en $0.");
            return;
        }

        String limpio = textoRecibido.trim();
        Double monto;
        if (limpio.equalsIgnoreCase("todo")) {
            monto = saldoActual;
        } else {
            String soloNumeros = limpio.replace("$", "").replace(".", "").replace(",", "").replace(" ", "");
            try {
                monto = Double.parseDouble(soloNumeros);
            } catch (NumberFormatException e) {
                telegramService.sendMessage(String.valueOf(telegramUserId),
                        "⚠️ No entendí ese monto. Escribe solo el número (ej: 25000) o *todo*.");
                return;
            }
        }

        if (monto <= 0) {
            telegramService.sendMessage(String.valueOf(telegramUserId), "⚠️ El monto debe ser mayor a $0.");
            return;
        }
        if (monto > saldoActual) {
            telegramService.sendMessage(String.valueOf(telegramUserId), String.format(
                    "⚠️ Ese monto supera lo que tienes en caja (*$%,.0f*). Escribe un monto menor o igual, o *todo*.",
                    saldoActual));
            return;
        }

        Supplier proveedor = supplierRepository.findById(pending.proveedorId()).orElse(null);
        if (proveedor == null) {
            pendingEntregas.remove(telegramUserId);
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ El proveedor ya no existe. Empieza de nuevo con el recordatorio de caja.");
            return;
        }

        try {
            movimientoService.registrarPagoProveedor(null, retirador.getEfectivo().getId(), null, proveedor.getId(),
                    null, monto);
        } catch (Exception e) {
            log.error("[Webhook] Error entregando a proveedor", e);
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ Hubo un error al registrar el pago. Intenta de nuevo.");
            return;
        }

        pendingEntregas.remove(telegramUserId);
        double restante = saldoActual - monto;

        String textoFinal;
        if (restante > 0) {
            textoFinal = String.format(
                    "✅ Entregaste *$%,.0f* a *%s*.\n\n⚠️ Aún tienes *$%,.0f* en caja. Por favor entrega el resto.",
                    monto, proveedor.getName(), restante);
            java.util.LinkedHashMap<String, String> buttonsData = new java.util.LinkedHashMap<>();
            buttonsData.put("✅ Entregar efectivo", "entregar_start");
            buttonsData.put("🧾 Registrar gasto", "gasto_start");
            telegramService.editMessageWithDynamicButtons(String.valueOf(telegramUserId), pending.messageId(),
                    textoFinal, buttonsData);
        } else {
            textoFinal = String.format(
                    "✅ *Entregado con éxito*\n\nEntregaste *$%,.0f* a *%s*.\nTu caja ahora está en *$0*.",
                    monto, proveedor.getName());
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), pending.messageId(), textoFinal);
        }

        log.info("[Entrega Efectivo] {} entregó ${} a {} (restante ${})",
                retirador.getNombre(), monto, proveedor.getName(), restante);
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
            telegramService.answerCallbackQuery(callbackQueryId,
                    "❌ Tu cuenta de Telegram no tiene @username configurado.");
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

        // ── Confirmar el retiro DE INMEDIATO: se salta el paso de "Ya hice el
        // retiro". Aceptar = confirmar, así que aquí mismo se resta el saldo de
        // la cuenta y se acredita la caja del retirador. ─────────────────────
        try {
            retiradorService.confirmarSolicitud(solicitudId);
        } catch (IllegalStateException e) {
            // Saldo insuficiente u otra validación de negocio: la solicitud queda
            // asignada (PENDIENTE) para poder confirmarla luego desde la app,
            // pero avisamos que no se completó todavía.
            telegramService.answerCallbackQuery(callbackQueryId, "❌ " + e.getMessage());
            if (solicitud.getTelegramMessageId() != null && !groupChatId.isBlank()) {
                telegramService.editMessage(groupChatId, solicitud.getTelegramMessageId(),
                        buildTextoTomado(solicitud, retirador));
            }
            return;
        }

        // Sin popup: los mensajes de abajo (grupo + privado) ya muestran el resultado.
        telegramService.answerCallbackQuery(callbackQueryId, "");

        // ── Editar el mensaje del grupo para indicar que ya quedó completado ──
        if (solicitud.getTelegramMessageId() != null && !groupChatId.isBlank()) {
            telegramService.editMessage(groupChatId, solicitud.getTelegramMessageId(),
                    buildTextoCompletado(solicitud, retirador));
        }

        // Obtener el retirador actualizado (con su caja ya acreditada)
        Retirador retiradorActualizado = retiradorRepository.findById(retirador.getId()).orElse(retirador);

        // ── Notificación privada al retirador: retiro ya completado ──────
        if (retiradorActualizado.getTelegramChatId() != null) {
            telegramService.sendMessage(String.valueOf(retiradorActualizado.getTelegramChatId()),
                    "✅ *Retiro completado* (Solicitud #" + solicitudId + ")");

            // Refrescar el recordatorio de caja (botón "Entregar efectivo") para
            // que quede como último mensaje del chat, reflejando el nuevo saldo.
            if (retiradorActualizado.getEfectivo() != null) {
                retiradorService.enviarRecordatorioCaja(retiradorActualizado);
            }
        }
    }

    private void handleCompleted(String callbackQueryId, String data, Long telegramUserId, Integer messageId) {
        long solicitudId = Long.parseLong(data.substring("completed:".length()));
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId).orElse(null);

        if (solicitud == null || solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            telegramService.answerCallbackQuery(callbackQueryId,
                    "⚠️ Esta solicitud ya no está pendiente (completada, cancelada, o no existe).");
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

        // Sin popup: el mensaje se edita abajo a "Retiro completado", ya es visible.
        telegramService.answerCallbackQuery(callbackQueryId, "");

        // Editar el mensaje original para quitar los botones
        if (messageId != null) {
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), messageId,
                    "✅ *Retiro completado* (Solicitud #" + solicitudId + ")");
        }

        // Refrescar el recordatorio de caja (botón "Entregar efectivo") para que
        // quede como último mensaje del chat, reflejando el nuevo saldo acumulado.
        if (retirador != null && retirador.getEfectivo() != null) {
            retiradorService.enviarRecordatorioCaja(retirador);
        }
    }

    private void handleCancel(String callbackQueryId, String data, Long telegramUserId, Integer messageId) {
        long solicitudId = Long.parseLong(data.substring("cancel:".length()));
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId).orElse(null);

        if (solicitud == null || solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            telegramService.answerCallbackQuery(callbackQueryId,
                    "⚠️ La solicitud no se puede cancelar en este estado.");
            return;
        }

        // Regresar a SIN_ASIGNAR
        solicitud.setRetirador(null);
        solicitud.setEstado(EstadoSolicitud.SIN_ASIGNAR);
        solicitudRepository.save(solicitud);

        // Reenviar mensaje al grupo
        retiradorService.reenviarSolicitudGeneral(solicitud);

        // Sin popup: el mensaje se edita abajo a "Retiro cancelado", ya es visible.
        telegramService.answerCallbackQuery(callbackQueryId, "");

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
        if (text == null)
            return;

        Map<String, Object> from = (Map<String, Object>) message.get("from");
        Long chatId = from != null ? toLong(from.get("id")) : null;

        // Si el retirador está en medio del flujo "Entregar efectivo" o
        // "Registrar gasto" y no escribió /start, tratamos el texto como la
        // respuesta a ese flujo (monto, o monto + descripción).
        if (chatId != null && !text.startsWith("/start")) {
            PendingEntrega pendingEntrega = pendingEntregas.get(chatId);
            if (pendingEntrega != null) {
                handleEntregarMonto(pendingEntrega, chatId, text);
                return;
            }
            PendingGasto pendingGasto = pendingGastos.get(chatId);
            if (pendingGasto != null) {
                handleGastoMonto(pendingGasto, chatId, text);
                return;
            }
        }

        if (!text.startsWith("/start"))
            return;

        String telegramUsername = from != null ? (String) from.get("username") : null;

        if (chatId == null)
            return;

        if (telegramUsername == null || telegramUsername.isBlank()) {
            telegramService.sendMessage(String.valueOf(chatId),
                    "⚠️ Tu cuenta de Telegram no tiene @username configurado. Agrégale uno en los ajustes de Telegram y vuelve a enviar /start.");
            return;
        }

        Optional<Retirador> optRetirador = findRetiradorByUsername(telegramUsername);
        if (optRetirador.isEmpty()) {
            log.warn("[Webhook] /start de @{} (chatId={}) no coincide con ningún retirador registrado.",
                    telegramUsername, chatId);
            telegramService.sendMessage(String.valueOf(chatId),
                    "⚠️ Tu usuario @" + telegramUsername + " no está registrado como retirador en el sistema. " +
                            "Pídele al administrador que verifique que tu @username esté bien escrito en tu registro, y vuelve a enviar /start.");
            return;
        }

        Retirador retirador = optRetirador.get();
        retirador.setTelegramChatId(chatId);
        retiradorRepository.save(retirador);
        log.info("[Webhook] Chat ID {} registrado para @{} ({})", chatId, telegramUsername, retirador.getNombre());

        String msg = "✅ ¡Hola *" + retirador.getNombre()
                + "*! Tu cuenta queda registrada para recibir notificaciones de retiro directamente.";
        if (groupInviteLink != null && !groupInviteLink.isBlank()) {
            msg += "\n\n👉 *Únete al grupo de retiradores aquí:* [Enlace al Grupo](" + groupInviteLink + ")";
        }

        telegramService.sendMessage(String.valueOf(chatId), msg);
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

    private String buildTextoCompletado(SolicitudRetiro solicitud, Retirador retirador) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ *Solicitud de Retiro #").append(solicitud.getId()).append(" — COMPLETADA*\n");
        sb.append("👤 *Retirador:* ").append(retirador.getNombre());
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
        if (o == null)
            return null;
        if (o instanceof Long l)
            return l;
        if (o instanceof Integer i)
            return i.longValue();
        return null;
    }

    private Optional<Retirador> findRetiradorByUsername(String telegramUsername) {
        if (telegramUsername == null)
            return Optional.empty();
        String cleanUsername = telegramUsername.startsWith("@") ? telegramUsername.substring(1) : telegramUsername;

        Optional<Retirador> opt = retiradorRepository.findByTelegramUsernameIgnoreCase(cleanUsername);
        if (opt.isPresent())
            return opt;

        return retiradorRepository.findByTelegramUsernameIgnoreCase("@" + cleanUsername);
    }
}
