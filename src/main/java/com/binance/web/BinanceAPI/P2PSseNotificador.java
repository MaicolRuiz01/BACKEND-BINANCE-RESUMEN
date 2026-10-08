package com.binance.web.BinanceAPI;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Avisos en vivo a las pantallas de P2P desde código que no es un bean (o que corre dentro de una transacción).
 *
 * - Se avisa DESPUÉS del commit, para que la pantalla, al volver a consultar, lea lo ya confirmado.
 * - Se agrupan los avisos: una tanda de cambios seguidos (p. ej. el Auto asignando varias ventas en un mismo
 *   ciclo) produce un solo mensaje, unos 250 ms después del primero. No se pierde ninguno: el mensaje sale
 *   después del último cambio de la tanda.
 */
public final class P2PSseNotificador {

    private static final long AGRUPAR_MS = 250L;

    private static final ScheduledExecutorService HILO = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "p2p-sse-notificador");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean ordenesPendiente = new AtomicBoolean(false);
    private static final AtomicBoolean cuentasPendiente = new AtomicBoolean(false);

    private P2PSseNotificador() {}

    /** Cambió la asignación de cuentas de alguna orden en curso. */
    public static void ordenesCambiaron() {
        trasCommit(() -> agrupar(ordenesPendiente, () -> {
            P2PSseController sse = P2PSseController.INSTANCE;
            if (sse != null) sse.broadcastCambioOrdenesActivas(1);
        }));
    }

    /** Una cuenta COP se activó o se desactivó para P2P. */
    public static void cuentasCambiaron() {
        trasCommit(() -> agrupar(cuentasPendiente, () -> {
            P2PSseController sse = P2PSseController.INSTANCE;
            if (sse != null) sse.broadcastCuentasP2PCambiaron();
        }));
    }

    private static void agrupar(AtomicBoolean pendiente, Runnable envio) {
        if (!pendiente.compareAndSet(false, true)) return; // ya hay un aviso programado: saldrá con este cambio incluido
        HILO.schedule(() -> {
            pendiente.set(false);
            try { envio.run(); } catch (Exception ignored) {}
        }, AGRUPAR_MS, TimeUnit.MILLISECONDS);
    }

    private static void trasCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }
}
