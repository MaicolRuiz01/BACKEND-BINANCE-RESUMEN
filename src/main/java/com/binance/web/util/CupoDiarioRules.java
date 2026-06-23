package com.binance.web.util;
import com.binance.web.Entity.BankType;

public final class CupoDiarioRules {

    private CupoDiarioRules() {} // evita instanciar

    /** Límite diario de retiro por CAJERO (ATM) según banco. */
    public static double maxCajeroPorBanco(BankType bank) {
        return switch (bank) {
            case NEQUI       -> 2_700_000.0;
            case BANCOLOMBIA -> 2_700_000.0;
            case DAVIPLATA   -> 3_000_000.0;
        };
    }

    /** Límite diario de retiro por CORRESPONSAL bancario según banco. */
    public static double maxCorresponsalPorBanco(BankType bank) {
        return switch (bank) {
            case NEQUI       ->  5_000_000.0;
            case BANCOLOMBIA -> 10_000_000.0;
            case DAVIPLATA   ->  5_000_000.0;
        };
    }
}
