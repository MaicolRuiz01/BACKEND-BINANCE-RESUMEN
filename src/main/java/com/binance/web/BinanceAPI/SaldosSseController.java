package com.binance.web.BinanceAPI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

/**
 * Server-Sent Events para saldos de cuentas COP en tiempo real.
 *
 * El frontend abre GET /saldos-events/subscribe (conexión persistente) y,
 * cuando cambia CUALQUIER saldo de cuenta COP, recibe el evento "saldos-cambiaron"
 * y vuelve a pedir la lista liviana de saldos (id + balance) para actualizar en el acto.
 *
 * Lo dispara AccountCopSaldoListener (después del commit) para no leer datos a medias.
 */
@Slf4j
@RestController
@RequestMapping("/saldos-events")
public class SaldosSseController {

    /** Referencia estática para que el @EntityListener (no es bean) pueda notificar. */
    public static SaldosSseController INSTANCE;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SaldosSseController() {
        INSTANCE = this;
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // sin timeout
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("ok", true)));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** Notifica a todos los clientes que hubo un cambio de saldo en cuentas COP. */
    public void notificarCambioSaldos() {
        if (emitters.isEmpty()) return;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter em : emitters) {
            try {
                em.send(SseEmitter.event().name("saldos-cambiaron").data(Map.of("t", System.currentTimeMillis())));
            } catch (Exception e) {
                dead.add(em);
                try { em.complete(); } catch (Exception ignored) {}
            }
        }
        emitters.removeAll(dead);
    }
}
