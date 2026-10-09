package com.binance.web.BinanceAPI;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events para notificaciones P2P en tiempo real.
 *
 * El frontend se suscribe UNA sola vez con GET /p2p-events/subscribe.
 * Cuando el scheduler detecta ventas nuevas, este controller las empuja
 * al browser — sin que el usuario tenga que refrescar la página.
 *
 * Flujo:
 *   1. Frontend abre  GET /p2p-events/subscribe  (conexión persistente)
 *   2. Scheduler sync → encuentra ventas nuevas
 *   3. Scheduler llama broadcast() → el frontend recibe evento SSE
 *   4. Frontend refresca la tabla automáticamente
 */
@Slf4j
@RestController
@RequestMapping("/p2p-events")
public class P2PSseController {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Lista thread-safe de clientes suscritos. */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** Referencia estática para que clases que no son beans (ver P2PSseNotificador) puedan avisar. */
    public static P2PSseController INSTANCE;

    public P2PSseController() {
        INSTANCE = this;
    }

    // ─────────────────────────────────────────────────────────────
    // Suscripción del cliente
    // ─────────────────────────────────────────────────────────────

    /**
     * El frontend llama a este endpoint una sola vez al montar la vista.
     * La conexión se mantiene viva hasta que el usuario navegue a otra página.
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // 0 = sin timeout (Spring maneja keepalive)

        emitters.add(emitter);
        log.debug("[SSE] Cliente suscrito. Total activos: {}", emitters.size());

        // Limpieza automática cuando el cliente desconecta
        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("[SSE] Cliente desconectado. Total activos: {}", emitters.size());
        });
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Evento inicial de conexión exitosa
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "Conectado al stream P2P",
                                 "hora",    LocalDateTime.now(ZONE).format(FMT))));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    // ─────────────────────────────────────────────────────────────
    // Broadcast desde el scheduler
    // ─────────────────────────────────────────────────────────────

    /**
     * Llamado por P2PSyncScheduler cuando se detectan ventas nuevas.
     * Envía un evento a todos los clientes suscritos.
     */
    public void broadcastNuevasVentas(int cantidad) {
        if (emitters.isEmpty()) return;

        String hora = LocalDateTime.now(ZONE).format(FMT);
        Map<String, Object> payload = Map.of(
                "tipo",     "nueva-venta-p2p",
                "cantidad", cantidad,
                "hora",     hora,
                "mensaje",  cantidad + " venta(s) P2P nueva(s) — " + hora
        );

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("nueva-venta-p2p")
                        .data(payload));
            } catch (Exception e) {
                dead.add(emitter);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }
        emitters.removeAll(dead);
        log.info("[SSE] Broadcast enviado ({} venta(s)) a {} cliente(s)", cantidad, emitters.size());
    }

    /**
     * Notifica a todos los clientes que hubo cambios en órdenes activas.
     * Llamado por el scheduler de órdenes activas cuando detecta cambios de estado.
     */
    public void broadcastCambioOrdenesActivas(int cantidad) {
        if (emitters.isEmpty()) return;

        String hora = LocalDateTime.now(ZONE).format(FMT);
        Map<String, Object> payload = Map.of(
                "tipo",     "cambio-orden-activa",
                "cantidad", cantidad,
                "hora",     hora,
                "mensaje",  cantidad + " orden(es) cambiaron de estado — " + hora
        );

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("cambio-orden-activa")
                        .data(payload));
            } catch (Exception e) {
                dead.add(emitter);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }
        emitters.removeAll(dead);
        log.info("[SSE] Broadcast cambio órdenes activas ({} cambio(s))", cantidad);
    }

    /**
     * Avisa a todas las pantallas que cambió la lista de cuentas COP de P2P (una se activó o se desactivó),
     * para que la recarguen sin que nadie tenga que darle al botón de actualizar.
     */
    public void broadcastCuentasP2PCambiaron() {
        if (emitters.isEmpty()) return;

        String hora = LocalDateTime.now(ZONE).format(FMT);
        Map<String, Object> payload = Map.of("tipo", "cuentas-p2p-cambiaron", "hora", hora);

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("cuentas-p2p-cambiaron").data(payload));
            } catch (Exception e) {
                dead.add(emitter);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }
        emitters.removeAll(dead);
    }

    /**
     * Avisa a todas las pantallas que llegó una venta GRANDE (por ejemplo, más de $5M): las pantallas muestran una
     * notificación flotante que no se quita sola. El payload dice el monto, la orden y si se asignó o quedó sin asignar.
     */
    public void broadcastVentaGrande(Map<String, Object> payload) {
        if (emitters.isEmpty()) return;

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("venta-grande").data(payload));
            } catch (Exception e) {
                dead.add(emitter);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }
        emitters.removeAll(dead);
        log.info("[SSE] Aviso de venta grande: {}", payload);
    }

    /**
     * Avisa que terminó (bien o mal) un envío automático de cuenta por el chat: las pantallas actualizan al
     * instante el "Enviando cuenta…" / "Cuenta enviada" en vez de esperar su consulta periódica.
     */
    public void broadcastChatEnvioActualizado() {
        if (emitters.isEmpty()) return;

        Map<String, Object> payload = Map.of("tipo", "chat-envio-actualizado",
                "hora", LocalDateTime.now(ZONE).format(FMT));

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("chat-envio-actualizado").data(payload));
            } catch (Exception e) {
                dead.add(emitter);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }
        emitters.removeAll(dead);
    }

    /**
     * Heartbeat periódico para mantener la conexión viva
     * (algunos proxies y navegadores cierran conexiones inactivas).
     * Llamado desde P2PSyncScheduler cada 30 segundos.
     */
    public void sendHeartbeat() {
        if (emitters.isEmpty()) return;
        List<SseEmitter> dead = new ArrayList<>();
        String hora = LocalDateTime.now(ZONE).format(FMT);
        for (SseEmitter emitter : emitters) {
            try {
                // Comentario SSE (": ping") — más transparente ante proxies que bufferean eventos
                emitter.send(SseEmitter.event()
                        .comment("ping " + hora));
                // Evento nombrado para que el frontend pueda monitorear la conexión
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data(hora));
            } catch (Exception e) {
                dead.add(emitter);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }
        emitters.removeAll(dead);
    }

    // ─────────────────────────────────────────────────────────────
    // Estado (debug)
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("clientesConectados", emitters.size());
    }
}
