package com.binance.web.service;

import java.util.List;
import java.util.Optional;

import com.binance.web.Entity.P2PAssignmentRule;

public interface P2PAssignmentRuleService {

    /**
     * Crea o actualiza la regla de auto-asignación para una cuenta Binance.
     * Si ya existe una regla para esa cuenta la reemplaza.
     *
     * @param binanceAccountName nombre de la cuenta Binance
     * @param copAccountId       ID de la cuenta COP destino
     * @return la regla guardada
     */
    P2PAssignmentRule setRule(String binanceAccountName, Integer copAccountId);

    /**
     * Devuelve la regla activa para una cuenta Binance.
     * Empty si no hay regla o está desactivada.
     */
    Optional<P2PAssignmentRule> getActiveRule(String binanceAccountName);

    /** Devuelve todas las reglas (activas e inactivas). */
    List<P2PAssignmentRule> getAllRules();

    /**
     * Pausa la auto-asignación para una cuenta Binance (active = false).
     * Las ventas importadas seguirán sin asignar hasta que se reactive.
     */
    void pauseRule(String binanceAccountName);

    /**
     * Reactiva la regla de una cuenta Binance (active = true).
     * Si no había regla no hace nada.
     */
    void resumeRule(String binanceAccountName);

    /** Elimina completamente la regla de una cuenta Binance. */
    void deleteRule(String binanceAccountName);
}
