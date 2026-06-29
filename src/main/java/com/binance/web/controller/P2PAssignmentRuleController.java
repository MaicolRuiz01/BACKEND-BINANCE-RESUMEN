package com.binance.web.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.service.SaleP2PService;

/**
 * Reasignación manual de ventas P2P.
 *
 * (La auto-asignación por reglas fue retirada a pedido del cliente; este
 *  controller conserva únicamente la reasignación puntual manual.)
 */
@RestController
@RequestMapping("/p2p-rule")
public class P2PAssignmentRuleController {

    @Autowired private SaleP2PService saleP2PService;

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
}
