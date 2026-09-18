package com.binance.web.BinanceAPI;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.binance.web.Entity.AccountCop;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * Listener JPA de AccountCop: cuando cambia un saldo (o se crea/borra una cuenta),
 * notifica por SSE a los clientes para que refresquen los saldos al instante.
 *
 * Se notifica DESPUÉS del commit (TransactionSynchronization) para que el frontend,
 * al volver a consultar, lea el valor ya confirmado y no uno a medias.
 */
public class AccountCopSaldoListener {

    @PostUpdate
    @PostPersist
    @PostRemove
    public void onCambio(AccountCop acc) {
        notificarTrasCommit();
    }

    /**
     * Avisa por SSE que cambiaron los saldos, después del commit si hay transacción.
     * También lo usan las pre-asignaciones: sus montos forman parte del verde/amarillo que se
     * pinta, así que asignar o marcar "ya cayó" debe refrescar todas las pantallas igual que
     * un cambio de saldo real.
     */
    public static void notificarTrasCommit() {
        final SaldosSseController sse = SaldosSseController.INSTANCE;
        if (sse == null) return;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try { sse.notificarCambioSaldos(); } catch (Exception ignored) {}
                }
            });
        } else {
            try { sse.notificarCambioSaldos(); } catch (Exception ignored) {}
        }
    }
}
