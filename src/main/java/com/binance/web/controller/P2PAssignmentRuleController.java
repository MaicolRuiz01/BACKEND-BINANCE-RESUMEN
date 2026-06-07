package com.binance.web.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.Entity.P2PAssignmentRule;
import com.binance.web.service.P2PAssignmentRuleService;
import com.binance.web.service.SaleP2PService;

/**
 * Gestión de reglas de auto-asignación P2P.
 *
 * Flujo típico:
 *   1. POST  /p2p-rule              → establece cuenta COP activa para una cuenta Binance
 *   2. GET   /p2p-rule/{account}    → consulta la regla vigente
 *   3. PUT   /p2p-rule/pause        → pausa (vuelve a modo manual)
 *   4. PUT   /p2p-rule/resume       → reactiva
 *   5. DELETE /p2p-rule/{account}   → elimina la regla
 *   6. PUT   /p2p-rule/reassign/{saleId}?copAccountId=X  → reasigna venta puntual
 */
@RestController
@RequestMapping("/p2p-rule")
public class P2PAssignmentRuleController {

    @Autowired private P2PAssignmentRuleService ruleService;
    @Autowired private SaleP2PService saleP2PService;

    // ──────────────────────────────────────────────────────────────
    // CRUD de regla
    // ──────────────────────────────────────────────────────────────

    /**
     * Establece (o cambia) la cuenta COP activa para una cuenta Binance.
     * Si ya existía una regla, la sobreescribe.
     *
     * Body: { "binanceAccount": "cuenta1", "copAccountId": 3 }
     */
    @PostMapping
    public ResponseEntity<P2PAssignmentRule> setRule(@RequestBody SetRuleRequest req) {
        P2PAssignmentRule rule = ruleService.setRule(req.getBinanceAccount(), req.getCopAccountId());
        return ResponseEntity.ok(rule);
    }

    /** Devuelve la regla activa de una cuenta Binance. 404 si no existe o está pausada. */
    @GetMapping("/{binanceAccount}")
    public ResponseEntity<P2PAssignmentRule> getRule(@PathVariable String binanceAccount) {
        return ruleService.getActiveRule(binanceAccount)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Lista todas las reglas (activas e inactivas). */
    @GetMapping
    public ResponseEntity<List<P2PAssignmentRule>> getAllRules() {
        return ResponseEntity.ok(ruleService.getAllRules());
    }

    /** Pausa la auto-asignación para una cuenta (modo manual). */
    @PutMapping("/{binanceAccount}/pause")
    public ResponseEntity<Void> pause(@PathVariable String binanceAccount) {
        ruleService.pauseRule(binanceAccount);
        return ResponseEntity.noContent().build();
    }

    /** Reactiva la auto-asignación para una cuenta. */
    @PutMapping("/{binanceAccount}/resume")
    public ResponseEntity<Void> resume(@PathVariable String binanceAccount) {
        ruleService.resumeRule(binanceAccount);
        return ResponseEntity.noContent().build();
    }

    /** Elimina la regla de una cuenta (vuelve a modo siempre-manual). */
    @DeleteMapping("/{binanceAccount}")
    public ResponseEntity<Void> deleteRule(@PathVariable String binanceAccount) {
        ruleService.deleteRule(binanceAccount);
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────────
    // Reasignación puntual
    // ──────────────────────────────────────────────────────────────

    /**
     * Reasigna una venta P2P ya asignada a otra cuenta COP.
     * Revierte el efecto en la cuenta anterior y aplica en la nueva.
     *
     * PUT /p2p-rule/reassign/42?copAccountId=5
     */
    @PutMapping("/reassign/{saleId}")
    public ResponseEntity<String> reassign(
            @PathVariable Integer saleId,
            @RequestParam Integer copAccountId) {
        String result = saleP2PService.reassignSale(saleId, copAccountId);
        return ResponseEntity.ok(result);
    }

    // ──────────────────────────────────────────────────────────────
    // DTO interno
    // ──────────────────────────────────────────────────────────────

    public static class SetRuleRequest {
        private String binanceAccount;
        private Integer copAccountId;

        public String getBinanceAccount() { return binanceAccount; }
        public void setBinanceAccount(String v) { this.binanceAccount = v; }
        public Integer getCopAccountId() { return copAccountId; }
        public void setCopAccountId(Integer v) { this.copAccountId = v; }
    }
}
