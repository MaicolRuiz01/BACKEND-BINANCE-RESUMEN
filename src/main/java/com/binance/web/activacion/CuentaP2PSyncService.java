package com.binance.web.activacion;

import com.binance.web.Entity.AccountCop;

/**
 * Punto único que conecta el estado "activaParaP2P" de una cuenta con el bot
 * de Movimientos. Nada más en el backend debería llamar directamente a
 * ActivacionService/DetencionService por cuenta propia — todos los lugares
 * que cambian activaParaP2P (selección automática de Maicol, el toggle
 * manual, el bloqueo de cuenta, el reset diario, y el auto-deselect por
 * error_login) pasan por acá, así el criterio de "cuándo avisarle al bot"
 * queda en un solo sitio.
 */
public interface CuentaP2PSyncService {

    /**
     * Compara el estado ANTES (estabaActivaAntes) contra el estado ACTUAL de
     * cuenta.getActivaParaP2P() y dispara activación o detención si cambió.
     * No hace nada si no cambió, si la cuenta es null, o si el banco no es
     * BANCOLOMBIA (único banco que el bot de Movimientos monitorea).
     */
    void sincronizar(AccountCop cuenta, boolean estabaActivaAntes);
}
