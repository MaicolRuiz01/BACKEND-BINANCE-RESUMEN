package com.binance.web.util;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;

import java.time.LocalDate;
import java.time.ZoneId;

public final class CupoDiarioRules {

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

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

    /**
     * Se asegura de que los cupos diarios de la cuenta estén al día: si cambió el
     * día (o nunca se inicializaron), los resetea a los máximos de su banco.
     *
     * Debe llamarse ANTES de leer o descontar cupoCajeroDisponibleHoy /
     * cupoCorresponsalDisponibleHoy, desde CUALQUIER flujo que gaste cupo diario
     * (movimientos directos vía MovimientoServiceImplement, o solicitudes de
     * retiro vía RetiradorServiceImpl) — ambos comparten el mismo contador por
     * cuenta, así que un retiro por cualquiera de los dos caminos consume del
     * mismo cupo del día.
     */
    public static void asegurarCupoHoy(AccountCop acc) {
        if (acc.getBankType() == null) {
            throw new IllegalStateException("La cuenta COP no tiene bankType");
        }

        LocalDate hoy = LocalDate.now(ZONE_BOGOTA);
        boolean diaDistinto = acc.getCupoFecha() == null || !hoy.equals(acc.getCupoFecha());

        if (diaDistinto
                || acc.getCupoCajeroDisponibleHoy() == null
                || acc.getCupoCorresponsalDisponibleHoy() == null) {
            double cajero = maxCajeroPorBanco(acc.getBankType());
            double corresponsal = maxCorresponsalPorBanco(acc.getBankType());
            acc.setCupoFecha(hoy);
            acc.setCupoCajeroDisponibleHoy(cajero);
            acc.setCupoCorresponsalDisponibleHoy(corresponsal);
            // legacy
            acc.setCupoDiarioMax(cajero + corresponsal);
            acc.setCupoDisponibleHoy(cajero + corresponsal);
        }
    }
}
