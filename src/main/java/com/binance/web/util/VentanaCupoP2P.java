package com.binance.web.util;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;

import com.binance.web.Entity.AccountCop;

/**
 * Regla horaria de la asignación P2P: en qué canal de retiro se trabaja según la hora.
 *
 *  - 00:00 → 18:29  CORRESPONSAL
 *  - 18:30 → 23:59  CAJERO
 *
 * Mientras dura una ventana se mira SOLO el cupo de ese canal y se ignora el otro (aunque la
 * cuenta tenga cupo de cajero de día, o de corresponsal de noche). El caso extremo en que se
 * llenan todas las cuentas del canal y toca pasar al otro NO está implementado todavía.
 *
 * Los montos van en MILES de COP, igual que el resto de cupos.
 */
public final class VentanaCupoP2P {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");
    /** Desde esta hora (inclusive) y hasta las 23:59 se trabaja con cajero. */
    public static final LocalTime INICIO_CAJERO = LocalTime.of(18, 30);

    public enum Canal { CAJERO, CORRESPONSAL }

    private VentanaCupoP2P() {}

    public static Canal canalAhora() {
        return canalAhora(Clock.system(ZONA));
    }

    public static Canal canalAhora(Clock reloj) {
        return canalEn(LocalTime.now(reloj.withZone(ZONA)));
    }

    public static Canal canalEn(LocalTime hora) {
        return hora.isBefore(INICIO_CAJERO) ? Canal.CORRESPONSAL : Canal.CAJERO;
    }

    /**
     * Cupo de retiro que le queda HOY a la cuenta en ese canal (MILES). Ya descuenta lo retirado
     * en el día. Llamar antes {@link CupoDiarioRules#asegurarCupoHoy} para que no sea de ayer.
     */
    public static double cupoHoy(AccountCop cuenta, Canal canal) {
        Double v = canal == Canal.CAJERO
                ? cuenta.getCupoCajeroDisponibleHoy()
                : cuenta.getCupoCorresponsalDisponibleHoy();
        return v != null ? v : 0.0;
    }
}
