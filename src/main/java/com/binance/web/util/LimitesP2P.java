package com.binance.web.util;

/**
 * Limites duros del sistema P2P.
 */
public final class LimitesP2P {

    /**
     * REGLA DE HIERRO: nunca mas de este numero de cuentas Bancolombia activas en P2P al mismo tiempo.
     * Cada cuenta activa es una sesion de Chrome corriendo en el computador donde se ejecuta Movimientos; con
     * mas de 7 la maquina no aguanta (RAM de Chrome). Cuenta TODAS las activas, tambien las que ya no reciben
     * ventas pero siguen monitoreadas esperando que se cierren las suyas. Aplica a la apertura automatica
     * (Auto) y a activar una cuenta a mano. (Era 8 con una "octava de emergencia"; se quito: siempre 7 como maximo.)
     */
    public static final int MAX_CUENTAS_ACTIVAS = 7;

    private LimitesP2P() {}
}
