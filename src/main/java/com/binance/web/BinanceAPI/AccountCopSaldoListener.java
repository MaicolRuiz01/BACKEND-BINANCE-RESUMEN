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
