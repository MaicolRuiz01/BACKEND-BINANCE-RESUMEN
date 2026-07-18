package com.binance.web.service;

import com.binance.web.Entity.*;
import com.binance.web.Repository.ClienteRepository;
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
    private final ClienteRepository clienteRepository;

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

    // Estado en memoria: retiradores que presionaron "✏️ Otra cifra" al
    // confirmar un retiro y están escribiendo el monto que realmente
    // retiraron (distinto al solicitado). Key = telegramUserId (chat_id privado).
    private final Map<Long, PendingMontoReal> pendingMontosReales = new ConcurrentHashMap<>();

    private record PendingMontoReal(Long solicitudId, Integer messageId) {
    }

    // Estado en memoria: retiradores que están en medio del flujo "Cliente
    // pagó" (efectivo que un cliente le entregó por USDT ya vendido) y ya
    // eligieron el cliente, esperando que escriban el monto.
    // Key = telegramUserId (chat_id privado).
    private final Map<Long, PendingClientePago> pendingClientePagos = new ConcurrentHashMap<>();

    private record PendingClientePago(Integer clienteId, String clienteNombre, Integer messageId) {
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
        } else if (data.startsWith("completed_todo:")) {
            handleCompletedTodo(callbackQueryId, data, telegramUserId, messageId);
        } else if (data.startsWith("completed_otra:")) {
            handleCompletedOtra(callbackQueryId, data, telegramUserId, messageId);
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
        } else if (data.equals("movimientos_start")) {
            handleMovimientosStart(callbackQueryId, telegramUserId, messageId);
        } else if (data.equals("cliente_pago_start")) {
            handleClientePagoStart(callbackQueryId, telegramUserId, messageId);
        } else if (data.startsWith("cliente_pago_sel:")) {
            handleClientePagoSel(callbackQueryId, data, telegramUserId, messageId);
        } else if (data.equals("cliente_pago_cancel")) {
            handleClientePagoCancel(callbackQueryId, telegramUserId, messageId);
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

        // Descartar cualquier flujo de entrega/gasto/monto-real/cliente-pago que hubiera quedado a medias
        pendingEntregas.remove(telegramUserId);
        pendingGastos.remove(telegramUserId);
        pendingMontosReales.remove(telegramUserId);
        pendingClientePagos.remove(telegramUserId);

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
                "💵 Vas a entregarle a *%s*.\n\nTienes en caja: *$%,.0f*\n\nEscribe el monto o *todo* para entregar toda la caja.",
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

        // Descartar cualquier flujo de entrega/monto-real/cliente-pago que hubiera quedado a medias
        pendingEntregas.remove(telegramUserId);
        pendingMontosReales.remove(telegramUserId);
        pendingClientePagos.remove(telegramUserId);
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
     * Botón "Cliente pagó": el retirador recibió efectivo de un cliente al que
     * ya se le vendió USDT (paga en efectivo en vez de transferencia). Pide
     * elegir el cliente entre los registrados.
     */
    private void handleClientePagoStart(String callbackQueryId, Long telegramUserId, Integer messageId) {
        Retirador retirador = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        if (retirador == null) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ No estás registrado como retirador.");
            return;
        }
        if (retirador.getEfectivo() == null) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ No se encontró tu caja.");
            return;
        }

        java.util.List<Cliente> clientes = clienteRepository.findAll();
        if (clientes.isEmpty()) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ No hay clientes registrados.");
            return;
        }

        // Descartar cualquier flujo de entrega/gasto/monto-real/cliente-pago que hubiera quedado a medias
        pendingEntregas.remove(telegramUserId);
        pendingGastos.remove(telegramUserId);
        pendingMontosReales.remove(telegramUserId);
        pendingClientePagos.remove(telegramUserId);

        java.util.LinkedHashMap<String, String> buttonsData = new java.util.LinkedHashMap<>();
        for (Cliente cliente : clientes) {
            buttonsData.put(cliente.getNombre(), "cliente_pago_sel:" + cliente.getId());
        }

        telegramService.editMessageWithDynamicButtons(
                String.valueOf(telegramUserId),
                messageId,
                "👤 *Selecciona el cliente que te entregó el efectivo:*",
                buttonsData);
        telegramService.answerCallbackQuery(callbackQueryId, "");
    }

    private void handleClientePagoSel(String callbackQueryId, String data, Long telegramUserId, Integer messageId) {
        Integer clienteId;
        try {
            clienteId = Integer.parseInt(data.substring("cliente_pago_sel:".length()));
        } catch (NumberFormatException e) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ Error en los datos del cliente.");
            return;
        }

        Retirador retirador = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        if (retirador == null || retirador.getEfectivo() == null) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ No se encontró tu caja.");
            return;
        }

        Cliente cliente = clienteRepository.findById(clienteId).orElse(null);
        if (cliente == null) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ El cliente ya no existe.");
            return;
        }

        // No registramos de inmediato: guardamos el estado y pedimos el monto.
        pendingClientePagos.put(telegramUserId, new PendingClientePago(cliente.getId(), cliente.getNombre(), messageId));

        String texto = String.format(
                "💵 *%s* te entregó efectivo por USDT ya vendido.\n\nEscribe el monto que recibiste.",
                cliente.getNombre());

        java.util.LinkedHashMap<String, String> buttonsData = new java.util.LinkedHashMap<>();
        buttonsData.put("❌ Cancelar", "cliente_pago_cancel");
        telegramService.editMessageWithDynamicButtons(String.valueOf(telegramUserId), messageId, texto, buttonsData);
        telegramService.answerCallbackQuery(callbackQueryId, "");
    }

    private void handleClientePagoCancel(String callbackQueryId, Long telegramUserId, Integer messageId) {
        pendingClientePagos.remove(telegramUserId);

        Retirador retirador = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        if (retirador == null || retirador.getEfectivo() == null || retirador.getEfectivo().getSaldo() <= 0) {
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), messageId, "Registro cancelado.");
            telegramService.answerCallbackQuery(callbackQueryId, "");
            return;
        }

        restaurarRecordatorio(telegramUserId, messageId, retirador);
        telegramService.answerCallbackQuery(callbackQueryId, "");
    }

    /**
     * Botón "Movimientos": responde de una vez con los movimientos (y ajustes)
     * de la caja del retirador SOLO DEL DÍA DE HOY — sin preguntar nada ni
     * esperar respuesta. El histórico completo solo se ve desde la plataforma.
     */
    private void handleMovimientosStart(String callbackQueryId, Long telegramUserId, Integer messageId) {
        Retirador retirador = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        if (retirador == null) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ No estás registrado como retirador.");
            return;
        }
        if (retirador.getEfectivo() == null) {
            telegramService.answerCallbackQuery(callbackQueryId, "⚠️ No se encontró tu caja.");
            return;
        }

        Integer cajaId = retirador.getEfectivo().getId();
        java.time.LocalDateTime desde = java.time.LocalDate.now(ZONE_BOGOTA).atStartOfDay();
        java.time.LocalDateTime hasta = java.time.LocalDateTime.now(ZONE_BOGOTA);

        java.util.List<com.binance.web.movimientos.MovimientoDTO> movimientos = movimientoService
                .listarMovimientosCajaLiteEntreFechas(cajaId, desde, hasta);
        java.util.List<Movimiento> ajustes = movimientoService.listarAjustesCajaEntreFechas(cajaId, desde, hasta);

        String reporte = buildReporteMovimientos(retirador, movimientos, ajustes);
        telegramService.sendMessage(String.valueOf(telegramUserId), reporte);

        // Sin popup: la respuesta ya se mandó como mensaje nuevo.
        telegramService.answerCallbackQuery(callbackQueryId, "");

        if (retirador.getEfectivo().getSaldo() > 0) {
            // Reenviamos el recordatorio de caja (3 botones) como mensaje NUEVO,
            // para que quede debajo del reporte de movimientos en vez de arriba.
            // enviarRecordatorioCaja ya se encarga de borrar el mensaje viejo.
            retiradorService.enviarRecordatorioCaja(retirador);
        } else {
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), messageId, "Tu caja está en $0.");
        }
    }

    /** Arma el texto del reporte de movimientos, ordenado por fecha descendente. */
    /** Nombre corto para mostrar en el reporte de Telegram: sin el prefijo "RETIRO"/"PAGO". */
    private String tipoParaMostrar(String tipo) {
        if (tipo == null)
            return "MOVIMIENTO";
        return switch (tipo) {
            case "RETIRO CAJERO" -> "CAJERO";
            case "RETIRO CORRESPONSAL" -> "CORRESPONSAL";
            case "PAGO PROVEEDOR" -> "PROVEEDOR";
            default -> tipo;
        };
    }

    private String buildReporteMovimientos(Retirador retirador,
            java.util.List<com.binance.web.movimientos.MovimientoDTO> movimientos,
            java.util.List<Movimiento> ajustes) {

        java.time.format.DateTimeFormatter fmtHora = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        java.util.List<java.util.AbstractMap.SimpleEntry<java.time.LocalDateTime, String>> entradas = new java.util.ArrayList<>();
        // Suma por tipo (GASTO, PAGO PROVEEDOR, RETIRO CAJERO, etc.), en el orden en
        // que se van encontrando, para el resumen de totales.
        java.util.LinkedHashMap<String, Double> totalesPorTipo = new java.util.LinkedHashMap<>();

        // ── Retiros COMPLETO (botón "Total"): un mismo retiro genera DOS movimientos
        // (RETIRO CAJERO + RETIRO CORRESPONSAL) sobre la misma cuenta, que pueden
        // confirmarse con algunos minutos de diferencia (ej. corresponsal ahora,
        // cajero 5 minutos después). Para cada cuenta, emparejamos cada CAJERO con
        // el CORRESPONSAL más cercano en el tiempo (el que menos minutos de
        // diferencia tenga), siempre que esa diferencia no pase de la ventana
        // permitida — así no se emparejan por accidente dos retiros realmente
        // distintos de la misma cuenta hechos con horas de diferencia.
        // Solo en este reporte de Telegram se muestran unificados — la plataforma
        // web sigue mostrándolos como dos movimientos separados, sin cambios ahí.
        final long VENTANA_MINUTOS = 15;

        java.util.Map<String, java.util.List<com.binance.web.movimientos.MovimientoDTO>> cajerosPorCuenta = new java.util.HashMap<>();
        java.util.Map<String, java.util.List<com.binance.web.movimientos.MovimientoDTO>> corresponsalesPorCuenta = new java.util.HashMap<>();
        for (var m : movimientos) {
            String tipo = m.getTipo() != null ? m.getTipo() : "";
            if ("RETIRO CAJERO".equals(tipo)) {
                cajerosPorCuenta.computeIfAbsent(String.valueOf(m.getCuentaOrigen()), k -> new java.util.ArrayList<>()).add(m);
            } else if ("RETIRO CORRESPONSAL".equals(tipo)) {
                corresponsalesPorCuenta.computeIfAbsent(String.valueOf(m.getCuentaOrigen()), k -> new java.util.ArrayList<>()).add(m);
            }
        }

        java.util.Map<com.binance.web.movimientos.MovimientoDTO, com.binance.web.movimientos.MovimientoDTO> parejaDe = new java.util.IdentityHashMap<>();
        java.util.Set<com.binance.web.movimientos.MovimientoDTO> yaUnificados = java.util.Collections
                .newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Set<com.binance.web.movimientos.MovimientoDTO> yaEmitidos = java.util.Collections
                .newSetFromMap(new java.util.IdentityHashMap<>());

        for (var entry : cajerosPorCuenta.entrySet()) {
            java.util.List<com.binance.web.movimientos.MovimientoDTO> corresponsalesDisponibles =
                    new java.util.ArrayList<>(corresponsalesPorCuenta.getOrDefault(entry.getKey(), java.util.Collections.emptyList()));
            for (var cajero : entry.getValue()) {
                if (cajero.getFecha() == null) continue;
                com.binance.web.movimientos.MovimientoDTO mejor = null;
                long mejorDiff = Long.MAX_VALUE;
                for (var corr : corresponsalesDisponibles) {
                    if (corr.getFecha() == null) continue;
                    long diff = Math.abs(java.time.Duration.between(cajero.getFecha(), corr.getFecha()).toMinutes());
                    if (diff <= VENTANA_MINUTOS && diff < mejorDiff) {
                        mejor = corr;
                        mejorDiff = diff;
                    }
                }
                if (mejor != null) {
                    corresponsalesDisponibles.remove(mejor);
                    parejaDe.put(cajero, mejor);
                    parejaDe.put(mejor, cajero);
                    yaUnificados.add(cajero);
                    yaUnificados.add(mejor);
                }
            }
        }

        for (var m : movimientos) {
            String tipo = m.getTipo() != null ? m.getTipo() : "MOVIMIENTO";
            String tipoCorto = tipoParaMostrar(tipo);
            double monto = m.getMonto() != null ? m.getMonto() : 0.0;
            totalesPorTipo.merge(tipoCorto, monto, Double::sum);

            if (yaUnificados.contains(m)) {
                if (yaEmitidos.contains(m)) {
                    continue; // su pareja ya generó el bloque unificado
                }
                var par = parejaDe.get(m);
                yaEmitidos.add(m);
                yaEmitidos.add(par);

                var cajeroMov = "RETIRO CAJERO".equals(m.getTipo()) ? m : par;
                var corresponsalMov = "RETIRO CORRESPONSAL".equals(m.getTipo()) ? m : par;
                double montoCajero = cajeroMov.getMonto() != null ? cajeroMov.getMonto() : 0.0;
                double montoCorresponsal = corresponsalMov.getMonto() != null ? corresponsalMov.getMonto() : 0.0;

                // Orden cronológico: el que ocurrió primero (sea cajero o corresponsal)
                // se muestra primero, con su hora en el reloj; si el segundo quedó en
                // un minuto distinto, su hora se agrega al lado con una flecha.
                boolean cajeroEsPrimero = cajeroMov.getFecha() != null && corresponsalMov.getFecha() != null
                        ? !cajeroMov.getFecha().isAfter(corresponsalMov.getFecha())
                        : true;
                var primero = cajeroEsPrimero ? cajeroMov : corresponsalMov;
                var segundo = cajeroEsPrimero ? corresponsalMov : cajeroMov;
                String horaPrimero = primero.getFecha() != null ? primero.getFecha().format(fmtHora) : "?";
                String horaSegundo = segundo.getFecha() != null ? segundo.getFecha().format(fmtHora) : "?";

                StringBuilder linea = new StringBuilder();
                linea.append("🕐 *").append(horaPrimero).append("* → *").append(horaSegundo).append("*\n");
                linea.append(tipoParaMostrar(primero.getTipo())).append(" — $")
                        .append(String.format("%,.0f", primero.getMonto() != null ? primero.getMonto() : 0.0)).append("\n");
                linea.append(tipoParaMostrar(segundo.getTipo())).append(" — $")
                        .append(String.format("%,.0f", segundo.getMonto() != null ? segundo.getMonto() : 0.0)).append("\n");
                linea.append("Total — $").append(String.format("%,.0f", montoCajero + montoCorresponsal));

                if (m.getCuentaOrigen() != null) {
                    linea.append("\n").append(m.getCuentaOrigen());
                }
                if (primero.getMotivo() != null && !primero.getMotivo().isBlank()) {
                    linea.append("\n").append(tipoParaMostrar(primero.getTipo())).append(": ").append(primero.getMotivo());
                }
                if (segundo.getMotivo() != null && !segundo.getMotivo().isBlank()) {
                    linea.append("\n").append(tipoParaMostrar(segundo.getTipo())).append(": ").append(segundo.getMotivo());
                }
                entradas.add(new java.util.AbstractMap.SimpleEntry<>(primero.getFecha(), linea.toString()));
                continue;
            }

            StringBuilder linea = new StringBuilder();
            linea.append("🕐 *").append(m.getFecha() != null ? m.getFecha().format(fmtHora) : "?").append("*\n");
            linea.append(tipoCorto).append(" — $").append(String.format("%,.0f", monto));

            // Nada de paréntesis (se ve mal en Telegram) — cada nombre va en su propia
            // línea, abajo del todo. Si hay más de uno (ej. traspaso origen→destino),
            // el segundo en adelante lleva una flechita para no perder el sentido.
            java.util.List<String> partes = new java.util.ArrayList<>();
            if (m.getCuentaOrigen() != null)
                partes.add(m.getCuentaOrigen());
            if (m.getPagoCliente() != null)
                partes.add(m.getPagoCliente());
            if (m.getCuentaDestino() != null)
                partes.add(m.getCuentaDestino());
            if (m.getCajaDestino() != null)
                partes.add(m.getCajaDestino());
            if (m.getPagoProveedor() != null)
                partes.add(m.getPagoProveedor());
            for (int i = 0; i < partes.size(); i++) {
                linea.append("\n").append(i > 0 ? "→ " : "").append(partes.get(i));
            }
            if (m.getMotivo() != null && !m.getMotivo().isBlank()) {
                linea.append("\n").append(m.getMotivo());
            }
            entradas.add(new java.util.AbstractMap.SimpleEntry<>(m.getFecha(), linea.toString()));
        }

        for (var a : ajustes) {
            String tipo = "AJUSTE DE SALDO";
            double monto = a.getMonto() != null ? a.getMonto() : 0.0;
            totalesPorTipo.merge(tipo, monto, Double::sum);

            StringBuilder linea = new StringBuilder();
            linea.append("🕐 *").append(a.getFecha() != null ? a.getFecha().format(fmtHora) : "?").append("*\n");
            linea.append("⚙️ ").append(tipo).append(" — $").append(String.format("%,.0f", monto));
            if (a.getMotivo() != null && !a.getMotivo().isBlank()) {
                linea.append(" (").append(a.getMotivo()).append(")");
            }
            entradas.add(new java.util.AbstractMap.SimpleEntry<>(a.getFecha(), linea.toString()));
        }

        entradas.sort((e1, e2) -> {
            if (e1.getKey() == null)
                return 1;
            if (e2.getKey() == null)
                return -1;
            return e2.getKey().compareTo(e1.getKey());
        });

        String fechaHoy = java.time.LocalDate.now(ZONE_BOGOTA)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Movimientos de hoy (").append(fechaHoy).append(")*\n");
        sb.append("💰 Caja actual: *$").append(String.format("%,.0f", retirador.getEfectivo().getSaldo()))
                .append("*\n\n");

        if (entradas.isEmpty()) {
            sb.append("_No ha habido movimientos hoy._");
            return sb.toString();
        }

        // ── Totales por tipo ──
        sb.append("📈 *Totales de hoy (").append(fechaHoy).append("):*\n");
        for (var e : totalesPorTipo.entrySet()) {
            sb.append("• ").append(e.getKey()).append(": $").append(String.format("%,.0f", e.getValue())).append("\n");
        }
        sb.append("\n");

        // ── Detalle, uno por bloque: hora arriba, detalle abajo ──
        int maxLineas = 40;
        int total = entradas.size();
        int limite = Math.min(total, maxLineas);
        java.util.List<String> bloques = new java.util.ArrayList<>();
        for (int i = 0; i < limite; i++) {
            bloques.add(entradas.get(i).getValue());
        }
        sb.append(String.join("\n\n", bloques));

        if (total > maxLineas) {
            sb.append("\n\n_...y ").append(total - maxLineas)
                    .append(" movimiento(s) más. Consulta la plataforma para ver el detalle completo._");
        }

        return sb.toString();
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

        Gasto gastoGuardado;
        try {
            Gasto gasto = new Gasto();
            gasto.setDescripcion(descripcion);
            gasto.setMonto(monto);
            gasto.setPagoEfectivo(retirador.getEfectivo());
            gasto.setIdempotencyKey("telegram-gasto-" + telegramUserId + "-" + System.currentTimeMillis());
            gastoGuardado = gastoService.saveGasto(gasto);
        } catch (Exception e) {
            log.error("[Webhook] Error registrando gasto", e);
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ Hubo un error al registrar el gasto. Intenta de nuevo.");
            return;
        }

        // Mismo resguardo que en "Entregar efectivo": releer la caja real de la BD
        // en vez de confiar en un cálculo local, para nunca confirmar algo que no
        // quedó realmente guardado.
        Retirador retiradorActualizado = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        Double saldoReal = retiradorActualizado != null && retiradorActualizado.getEfectivo() != null
                ? retiradorActualizado.getEfectivo().getSaldo()
                : null;
        double restante = saldoActual - monto;
        boolean seReflejoEnCaja = gastoGuardado != null && gastoGuardado.getId() != null
                && saldoReal != null && Math.abs(saldoReal - restante) < 1.0;

        if (!seReflejoEnCaja) {
            log.error(
                    "[Webhook] Gasto de ${} ({}) no se vio reflejado en la caja (esperado ${}, real {}). No se confirma al retirador.",
                    monto, descripcion, restante, saldoReal);
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ No se pudo confirmar que el gasto haya quedado registrado. Vuelve a intentar en un momento.");
            return;
        }

        pendingGastos.remove(telegramUserId);

        String textoFinal = restante > 0
                ? String.format("✅ Gasto registrado: *$%,.0f* — %s.\n\n💰 Caja: *$%,.0f*", monto, descripcion,
                        restante)
                : String.format("✅ Gasto registrado: *$%,.0f* — %s.\n\nTu caja ahora está en *$0*.", monto,
                        descripcion);
        telegramService.sendMessage(String.valueOf(telegramUserId), textoFinal);

        // Reenviamos el recordatorio de caja (3 botones) como mensaje NUEVO, para
        // que quede debajo de la confirmación en vez de arriba; esto también borra
        // el mensaje viejo (el que pedía el monto y la descripción).
        retiradorService.enviarRecordatorioCaja(retirador);

        log.info("[Gasto] {} registró un gasto de ${} ({}) — restante ${}",
                retirador.getNombre(), monto, descripcion, restante);
    }

    /**
     * Reconstruye el mensaje de recordatorio de caja con sus botones
     * habituales.
     */
    private void restaurarRecordatorio(Long telegramUserId, Integer messageId, Retirador retirador) {
        String texto = String.format("💰 Caja: *$%,.0f*", retirador.getEfectivo().getSaldo());
        java.util.LinkedHashMap<String, String> buttonsData = new java.util.LinkedHashMap<>();
        buttonsData.put("✅ Entregar efectivo", "entregar_start");
        buttonsData.put("🧾 Registrar gasto", "gasto_start");
        buttonsData.put("📊 Movimientos", "movimientos_start");
        buttonsData.put("💵 Cliente pagó", "cliente_pago_start");
        telegramService.editMessageWithDynamicButtons(String.valueOf(telegramUserId), messageId, texto, buttonsData);
    }

    /**
     * Procesa el monto que el retirador escribió como respuesta al flujo
     * "Cliente pagó": efectivo que un cliente le entregó porque ya se le
     * vendió USDT y decidió pagar en efectivo en vez de transferencia. No
     * reinventa nada — usa la misma función que ya existía en Movimientos
     * (movimientoService.registrarPagoCaja), la misma que ya usa la caja/el
     * saldo del cliente en la plataforma web.
     */
    private void handleClientePagoMonto(PendingClientePago pending, Long telegramUserId, String textoRecibido) {
        Retirador retirador = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        if (retirador == null || retirador.getEfectivo() == null) {
            pendingClientePagos.remove(telegramUserId);
            telegramService.sendMessage(String.valueOf(telegramUserId), "⚠️ No se encontró tu caja.");
            return;
        }

        String soloNumeros = textoRecibido.trim().replace("$", "").replace(".", "").replace(",", "").replace(" ", "");
        Double monto;
        try {
            monto = Double.parseDouble(soloNumeros);
        } catch (NumberFormatException e) {
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ No entendí ese monto. Escribe solo el número, ej: `50000`.");
            return;
        }

        if (monto <= 0) {
            telegramService.sendMessage(String.valueOf(telegramUserId), "⚠️ El monto debe ser mayor a $0.");
            return;
        }

        try {
            movimientoService.registrarPagoCaja(pending.clienteId(), retirador.getEfectivo().getId(), monto);
        } catch (Exception e) {
            log.error("[Webhook] Error registrando pago de cliente a caja", e);
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ Hubo un error al registrar el pago. Intenta de nuevo.");
            return;
        }

        pendingClientePagos.remove(telegramUserId);

        telegramService.sendMessage(String.valueOf(telegramUserId), String.format(
                "✅ Pago registrado: *$%,.0f* recibidos de *%s*.", monto, pending.clienteNombre()));

        // Releemos el retirador fresco de la BD (mismo resguardo que usa "Registrar
        // gasto") en vez de reusar el objeto cargado al principio del método, para
        // no depender de que el saldo en memoria haya quedado sincronizado con el
        // pago que se acaba de registrar.
        Retirador retiradorActualizado = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(retirador);
        log.info("[Cliente Pago] Enviando recordatorio de caja tras el pago — retirador={}, cajaId={}, saldo={}",
                retiradorActualizado.getNombre(),
                retiradorActualizado.getEfectivo() != null ? retiradorActualizado.getEfectivo().getId() : null,
                retiradorActualizado.getEfectivo() != null ? retiradorActualizado.getEfectivo().getSaldo() : null);
        retiradorService.enviarRecordatorioCaja(retiradorActualizado);

        log.info("[Cliente Pago] {} registró un pago en efectivo de ${} de {}",
                retiradorActualizado.getNombre(), monto, pending.clienteNombre());
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

        Movimiento movRegistrado;
        try {
            movRegistrado = movimientoService.registrarPagoProveedor(null, retirador.getEfectivo().getId(), null,
                    proveedor.getId(), null, monto);
        } catch (Exception e) {
            log.error("[Webhook] Error entregando a proveedor", e);
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ Hubo un error al registrar el pago. Intenta de nuevo.");
            return;
        }

        // No confiamos en un cálculo local (saldoActual - monto) para el mensaje de
        // éxito: releemos la caja YA GUARDADA en base de datos. Así, si por lo que
        // sea la escritura no se reflejó de verdad (sin lanzar excepción — pasó una
        // vez en producción: Telegram confirmó un pago que nunca quedó en la BD),
        // no le confirmamos al retirador algo que no ocurrió.
        Retirador retiradorActualizado = retiradorRepository.findByTelegramChatId(telegramUserId).orElse(null);
        Double saldoReal = retiradorActualizado != null && retiradorActualizado.getEfectivo() != null
                ? retiradorActualizado.getEfectivo().getSaldo()
                : null;
        double restante = saldoActual - monto;
        boolean seReflejoEnCaja = movRegistrado != null && movRegistrado.getId() != null
                && saldoReal != null && Math.abs(saldoReal - restante) < 1.0;

        if (!seReflejoEnCaja) {
            log.error(
                    "[Webhook] Pago de ${} a {} no se vio reflejado en la caja (esperado ${}, real {}). No se confirma al retirador.",
                    monto, proveedor.getName(), restante, saldoReal);
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ No se pudo confirmar que el pago haya quedado registrado. NO lo des por hecho — avisa al administrador antes de entregar el dinero, y vuelve a intentar.");
            return;
        }

        pendingEntregas.remove(telegramUserId);

        String textoFinal = restante > 0
                ? String.format(
                        "✅ Entregaste *$%,.0f* a *%s*.\n\n⚠️ Aún tienes *$%,.0f* en caja. Por favor entrega el resto.",
                        monto, proveedor.getName(), restante)
                : String.format(
                        "✅ *Entregado con éxito*\n\nEntregaste *$%,.0f* a *%s*.\nTu caja ahora está en *$0*.",
                        monto, proveedor.getName());
        telegramService.sendMessage(String.valueOf(telegramUserId), textoFinal);

        // Reenviamos el recordatorio de caja (3 botones) como mensaje NUEVO, para
        // que quede debajo de la confirmación en vez de arriba; esto también borra
        // el mensaje viejo (el que pedía el monto a entregar).
        retiradorService.enviarRecordatorioCaja(retirador);

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

        // Sin popup: los mensajes de abajo (grupo + privado) ya muestran el resultado.
        telegramService.answerCallbackQuery(callbackQueryId, "");

        // ── Editar el mensaje del grupo para indicar que ya fue tomada ────────
        if (solicitud.getTelegramMessageId() != null && !groupChatId.isBlank()) {
            telegramService.editMessage(groupChatId, solicitud.getTelegramMessageId(),
                    buildTextoTomado(solicitud, retirador));
        }

        // ── Mensaje privado al retirador con los dos botones: "Ya hice el
        // retiro" / "Cancelar". El retiro NO se confirma acá — se confirma
        // recién cuando el retirador presiona "Ya hice el retiro" desde el
        // privado (handleCompleted), igual que en el flujo de solicitud directa. ──
        if (retirador.getTelegramChatId() != null) {
            Integer privateMessageId = telegramService.sendMessageWithTwoButtons(
                    String.valueOf(retirador.getTelegramChatId()),
                    buildMensajePrivado(solicitud, retirador),
                    "✅ Ya hice el retiro", "completed:" + solicitud.getId(),
                    "❌ Cancelar", "cancel:" + solicitud.getId());
            solicitud.setTelegramPrivateMessageId(privateMessageId);
            solicitudRepository.save(solicitud);
        }
    }

    /**
     * El retirador presionó "✅ Ya hice el retiro". Antes esto confirmaba de
     * una vez con el monto solicitado; ahora primero preguntamos si retiró
     * exactamente eso o le tocó con otra cifra (ej: el corresponsal solo dejó
     * sacar $9.400 de los $9.500 pedidos), porque eso cambia lo que hay que
     * descontar de la cuenta y acreditar a la caja.
     */
    private void handleCompleted(String callbackQueryId, String data, Long telegramUserId, Integer messageId) {
        long solicitudId = Long.parseLong(data.substring("completed:".length()));
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId).orElse(null);

        if (solicitud == null || solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            telegramService.answerCallbackQuery(callbackQueryId,
                    "⚠️ Esta solicitud ya no está pendiente (completada, cancelada, o no existe).");
            return;
        }

        telegramService.answerCallbackQuery(callbackQueryId, "");

        if (messageId != null) {
            String texto = "¿Retiraste todo?";
            java.util.LinkedHashMap<String, String> buttonsData = new java.util.LinkedHashMap<>();
            buttonsData.put("✅ Retiré todo", "completed_todo:" + solicitudId);
            buttonsData.put("✏️ Otra cifra", "completed_otra:" + solicitudId);
            telegramService.editMessageWithDynamicButtons(String.valueOf(telegramUserId), messageId, texto, buttonsData);
        }
    }

    /** "✅ Retiré todo": confirma con el monto tal cual se solicitó (comportamiento de siempre). */
    private void handleCompletedTodo(String callbackQueryId, String data, Long telegramUserId, Integer messageId) {
        long solicitudId = Long.parseLong(data.substring("completed_todo:".length()));
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId).orElse(null);

        if (solicitud == null || solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            telegramService.answerCallbackQuery(callbackQueryId,
                    "⚠️ Esta solicitud ya no está pendiente (completada, cancelada, o no existe).");
            return;
        }

        try {
            retiradorService.confirmarSolicitud(solicitudId);
        } catch (IllegalStateException e) {
            telegramService.answerCallbackQuery(callbackQueryId, "❌ " + e.getMessage());
            return;
        }

        Retirador retirador = retiradorRepository.findById(solicitud.getRetirador().getId()).orElse(null);
        telegramService.answerCallbackQuery(callbackQueryId, "");

        if (messageId != null) {
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), messageId,
                    "✅ *Retiro completado* (Solicitud #" + solicitudId + ")");
        }

        if (retirador != null && retirador.getEfectivo() != null) {
            retiradorService.enviarRecordatorioCaja(retirador);
        }
    }

    /** "✏️ Otra cifra": pide que escriba el monto que realmente retiró. */
    private void handleCompletedOtra(String callbackQueryId, String data, Long telegramUserId, Integer messageId) {
        long solicitudId = Long.parseLong(data.substring("completed_otra:".length()));
        SolicitudRetiro solicitud = solicitudRepository.findById(solicitudId).orElse(null);

        if (solicitud == null || solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            telegramService.answerCallbackQuery(callbackQueryId,
                    "⚠️ Esta solicitud ya no está pendiente (completada, cancelada, o no existe).");
            return;
        }

        telegramService.answerCallbackQuery(callbackQueryId, "");

        pendingMontosReales.put(telegramUserId, new PendingMontoReal(solicitudId, messageId));

        if (messageId != null) {
            telegramService.editMessageTextOnly(String.valueOf(telegramUserId), messageId, "Escribe el monto");
        }
    }

    /**
     * Procesa el texto que el retirador escribió como respuesta a "✏️ Otra
     * cifra": el monto REAL que le dejaron retirar, distinto al solicitado.
     * Confirma la solicitud usando esa cifra en vez de la original.
     */
    private void handleMontoRealTexto(PendingMontoReal pending, Long telegramUserId, String textoRecibido) {
        SolicitudRetiro solicitud = solicitudRepository.findById(pending.solicitudId()).orElse(null);
        if (solicitud == null || solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            pendingMontosReales.remove(telegramUserId);
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ Esta solicitud ya no está pendiente (completada, cancelada, o no existe).");
            return;
        }

        double montoSolicitado = solicitud.getTotalMonto();
        String soloNumeros = textoRecibido.trim().replace("$", "").replace(".", "").replace(",", "").replace(" ", "");
        Double montoReal;
        try {
            montoReal = Double.parseDouble(soloNumeros);
        } catch (NumberFormatException e) {
            telegramService.sendMessage(String.valueOf(telegramUserId),
                    "⚠️ No entendí ese monto. Escribe solo el número, ej: `9400`.");
            return;
        }

        if (montoReal <= 0) {
            telegramService.sendMessage(String.valueOf(telegramUserId), "⚠️ El monto debe ser mayor a $0.");
            return;
        }

        try {
            retiradorService.confirmarSolicitudConMontoReal(pending.solicitudId(), montoReal);
        } catch (IllegalStateException e) {
            telegramService.sendMessage(String.valueOf(telegramUserId), "❌ " + e.getMessage() + "\n\nEscribe otra vez el monto correcto.");
            return;
        }

        pendingMontosReales.remove(telegramUserId);

        Retirador retirador = retiradorRepository.findById(solicitud.getRetirador().getId()).orElse(null);

        String textoFinal = String.format(
                "✅ *Retiro registrado* (Solicitud #%d)\n\nSolicitado: *$%,.0f*\nRetiraste: *$%,.0f*\n\nQuedó anotado en movimientos.",
                pending.solicitudId(), montoSolicitado, montoReal);
        telegramService.sendMessage(String.valueOf(telegramUserId), textoFinal);

        if (retirador != null && retirador.getEfectivo() != null) {
            retiradorService.enviarRecordatorioCaja(retirador);
        }

        log.info("[Retiro] Solicitud #{} confirmada con monto REAL distinto: solicitado ${} → real ${}",
                pending.solicitudId(), montoSolicitado, montoReal);
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
            PendingMontoReal pendingMontoReal = pendingMontosReales.get(chatId);
            if (pendingMontoReal != null) {
                handleMontoRealTexto(pendingMontoReal, chatId, text);
                return;
            }
            PendingClientePago pendingClientePago = pendingClientePagos.get(chatId);
            if (pendingClientePago != null) {
                handleClientePagoMonto(pendingClientePago, chatId, text);
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
        // Mismo formato simple que el retiro directo: solo monto y cuenta/banco,
        // sin encabezado ni nombre del retirador (es su chat privado).
        StringBuilder sb = new StringBuilder();
        sb.append("💰 *Total:* $").append(String.format("%,.0f", solicitud.getTotalMonto())).append(" COP\n");
        sb.append("🏦 ");
        for (int i = 0; i < solicitud.getDetalles().size(); i++) {
            DetalleRetiro d = solicitud.getDetalles().get(i);
            if (i > 0)
                sb.append(", ");
            String banco = d.getCuentaCop().getBankType() != null ? d.getCuentaCop().getBankType().name() : "?";
            sb.append(d.getCuentaCop().getName()).append(" (").append(banco).append(")");
        }
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
