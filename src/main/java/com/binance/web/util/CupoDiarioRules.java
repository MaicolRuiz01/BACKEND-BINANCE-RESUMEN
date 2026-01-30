package com.binance.web.util;
import com.binance.web.Entity.BankType; 

public final class CupoDiarioRules {

    private CupoDiarioRules() {} // evita instanciar

    public static double maxPorBanco(BankType bank) {
        return switch (bank) {
            case NEQUI -> 7700000.0;
            case BANCOLOMBIA -> 12700000.0;
            case DAVIPLATA -> 8000000.0;
        };
    }
}
