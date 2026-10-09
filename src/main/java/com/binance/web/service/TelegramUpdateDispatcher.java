package com.binance.web.service;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Procesa los updates de Telegram FUERA del hilo HTTP del webhook.
 *
 * El webhook debe responder 200 al instante: si el backend tarda (base de datos lenta, llamadas a Telegram, un
 * reinicio), Telegram reintenta, acumula updates y los botones se ven "cargando" o muertos. Aqui cada update se
 * encola y se procesa en segundo plano.
 *
 * Los updates de UN MISMO usuario salen siempre por la misma cola (de un solo hilo), asi que se procesan en el orden
 * en que llegaron (importante para los flujos de varios pasos); usuarios distintos se procesan en paralelo.
 */
@Slf4j
@Component
public class TelegramUpdateDispatcher {

    private static final int COLAS = 4;
    private static final int CAPACIDAD_POR_COLA = 500;

    private final ExecutorService[] colas = new ExecutorService[COLAS];

    public TelegramUpdateDispatcher() {
        for (int i = 0; i < COLAS; i++) {
            final int n = i;
            colas[i] = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(CAPACIDAD_POR_COLA),
                    r -> {
                        Thread t = new Thread(r, "telegram-update-" + n);
                        t.setDaemon(true);
                        return t;
                    },
                    // Cola llena (no deberia pasar): se procesa en el hilo del webhook antes que perder el update.
                    new ThreadPoolExecutor.CallerRunsPolicy());
        }
    }

    /** Encola el update; la tarea es lo que de verdad lo procesa. */
    public void despachar(Map<String, Object> update, Runnable tarea) {
        long clave = claveDe(update);
        colas[(int) Math.floorMod(clave, (long) COLAS)].execute(() -> {
            try {
                tarea.run();
            } catch (Exception e) {
                log.error("[Webhook] Error procesando update: {}", e.getMessage(), e);
            }
        });
    }

    /** Identifica al usuario del update (callback_query.from.id o message.from.id); 0 si no se puede. */
    @SuppressWarnings("unchecked")
    static long claveDe(Map<String, Object> update) {
        try {
            for (String tipo : new String[]{"callback_query", "message"}) {
                Object cuerpo = update.get(tipo);
                if (cuerpo instanceof Map<?, ?> m) {
                    Object from = ((Map<String, Object>) m).get("from");
                    if (from instanceof Map<?, ?> f && f.get("id") instanceof Number id) {
                        return id.longValue();
                    }
                }
            }
        } catch (Exception ignored) {
            // si el update viene raro, cae en la cola 0
        }
        return 0L;
    }

    @PreDestroy
    void cerrar() {
        for (ExecutorService c : colas) c.shutdown();
    }
}
