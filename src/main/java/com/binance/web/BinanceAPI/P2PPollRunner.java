package com.binance.web.BinanceAPI;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Hilo PROPIO y exclusivo para la revisión de órdenes P2P en curso.
 *
 * POR QUÉ EXISTE: antes esta revisión era una @Scheduled más, compartiendo el pool de tareas
 * programadas con otras doce (Bybit, compras, traspasos, conciliación, vigilancia de jornada…).
 * Varias de esas hacen llamadas lentas a APIs externas y ocupan un hilo durante segundos, o se
 * quedan tomadas. Cuando eso pasaba, la revisión de órdenes QUEDABA EN COLA y no corría nunca:
 * no fallaba nada, no salía ningún error, simplemente los saldos dejaban de actualizarse.
 *
 * En los logs eso se veía como silencio absoluto — ni una línea de [ActivePoll] mientras entraban
 * ventas — y costó días de diagnóstico, porque se parece mucho a "no hay cambios que reportar".
 *
 * Con su propio hilo, la actualización de saldos no depende de lo que haga el resto del sistema.
 */
@Slf4j
@Component
public class P2PPollRunner {

    @Autowired private P2PSyncScheduler scheduler;

    @Value("${p2p.active-poll-ms:5000}")
    private long pollMs;

    private ScheduledExecutorService ejecutor;

    @PostConstruct
    public void iniciar() {
        ejecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "p2p-poll");
            t.setDaemon(true);
            return t;
        });
        // scheduleWithFixedDelay: el intervalo cuenta ENTRE ejecuciones, así que si una vuelta se
        // demora (Binance lento), la siguiente no se le encima.
        ejecutor.scheduleWithFixedDelay(this::vuelta, 10_000L, pollMs, TimeUnit.MILLISECONDS);
        log.info("[P2P] Revisión de órdenes en curso corriendo en su propio hilo, cada {} ms", pollMs);
    }

    private void vuelta() {
        try {
            scheduler.pollActiveOrders();
        } catch (Throwable t) {
            // Throwable y no Exception: si se escapa un Error, este hilo moriría y los saldos
            // dejarían de actualizarse en silencio, que es justo lo que queremos evitar.
            log.error("[ActivePoll] Falló una vuelta de la revisión de órdenes: {}", t.getMessage(), t);
        }
    }

    @PreDestroy
    public void detener() {
        if (ejecutor != null) ejecutor.shutdownNow();
    }
}
