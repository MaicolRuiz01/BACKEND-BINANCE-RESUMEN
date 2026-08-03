package com.binance.web.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.binance.web.Entity.Usuario;

import lombok.extern.slf4j.Slf4j;

/**
 * Server-Sent Events de la jornada, para avisarle al operador EN EL ACTO.
 *
 * El frontend abre GET /jornada-events/subscribe?username=xxx y recibe:
 *   · "aviso"  → hay que mostrarle un mensaje (ej: bájale un punto a la tasa)
 *   · "pausa"  → se le detuvo el cronómetro
 *   · "reanudada" → volvió a correr
 *
 * Se filtra por username para que un operador no reciba los avisos de otro.
 *
 * IMPORTANTE: esto es el canal RÁPIDO, no el confiable. El estado real vive en la BD y el
 * frontend igual lo consulta cada 30 s, porque en Railway estas conexiones se caen seguido.
 * Si el SSE falla, el operador se entera igual, solo que unos segundos más tarde.
 */
@Slf4j
@RestController
@RequestMapping("/jornada-events")
public class JornadaSseController {

    /** Referencia estática para que el scheduler pueda notificar sin dependencia circular. */
    public static JornadaSseController INSTANCE;

    /** Emisor + a qué operador pertenece. */
    private record Suscriptor(String username, SseEmitter emitter) {}

    private final List<Suscriptor> suscriptores = new CopyOnWriteArrayList<>();

    public JornadaSseController() {
        INSTANCE = this;
    }

    /**
     * El operador se suscribe a SUS propios eventos.
     *
     * El usuario se toma del token, NO de un parámetro: si se recibiera por query, cualquier
     * operador autenticado podría suscribirse al canal de otro y ver sus llamados de atención.
     * El token viaja como ?token= porque EventSource no permite mandar cabeceras (ya lo
     * contempla JwtAuthFilter).
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal Usuario user) {
        SseEmitter emitter = new SseEmitter(0L); // sin timeout
        String username = user != null ? user.getUsername() : null;
        Suscriptor s = new Suscriptor(username, emitter);
        suscriptores.add(s);

        emitter.onCompletion(() -> suscriptores.remove(s));
        emitter.onTimeout(() -> suscriptores.remove(s));
        emitter.onError(e -> suscriptores.remove(s));

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("ok", true)));
        } catch (IOException e) {
            suscriptores.remove(s);
        }
        return emitter;
    }

    /** Le manda un aviso al operador (mensaje para mostrar en pantalla). */
    public void notificarAviso(String username, String mensaje) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mensaje", mensaje);
        data.put("t", System.currentTimeMillis());
        emitir(username, "aviso", data);
    }

    /** Le avisa que se le pausó el cronómetro, con el motivo. */
    public void notificarPausa(String username, String motivo) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("motivo", motivo);
        data.put("t", System.currentTimeMillis());
        emitir(username, "pausa", data);
    }

    /** Le avisa que su jornada volvió a correr. */
    public void notificarReanudada(String username) {
        emitir(username, "reanudada", Map.of("t", System.currentTimeMillis()));
    }

    private void emitir(String username, String evento, Object data) {
        if (username == null || suscriptores.isEmpty()) return;
        List<Suscriptor> muertos = new ArrayList<>();
        for (Suscriptor s : suscriptores) {
            if (!username.equals(s.username())) continue;
            try {
                s.emitter().send(SseEmitter.event().name(evento).data(data));
            } catch (Exception e) {
                muertos.add(s);
                try { s.emitter().complete(); } catch (Exception ignored) {}
            }
        }
        suscriptores.removeAll(muertos);
    }
}
