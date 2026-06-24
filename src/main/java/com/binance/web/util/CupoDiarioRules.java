package com.binance.web.util;
import com.binance.web.Entity.BankType;

public final class CupoDiarioRules {

    private CupoDiarioRules() {} // evita instanciar

    /**
     * Límite diario de retiro por CAJERO (ATM) según banco.
     * Valores en MILES de COP (misma unidad que pesosCop en SaleP2P).
     * 2_700_000 COP → 2_700 miles
     */
    public static double maxCajeroPorBanco(BankType bank) {
        return switch (bank) {
            case NEQUI       -> 2_700.0;
            case BANCOLOMBIA -> 2_700.0;
            case DAVIPLATA   -> 3_000.0;
        };
    }

    /**
     * Límite diario de retiro por CORRESPONSAL bancario según banco.
     * Valores en MILES de COP (misma unidad que pesosCop en SaleP2P).
     * 5_000_000 COP → 5_000 miles
     */
    public static double maxCorresponsalPorBanco(BankType bank) {
        return switch (bank) {
            case NEQUI       ->  5_000.0;
            case BANCOLOMBIA -> 10_000.0;
            case DAVIPLATA   ->  5_000.0;
        };
    }
}
