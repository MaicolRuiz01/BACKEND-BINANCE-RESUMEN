package com.binance.web.Entity;

public enum EstadoSolicitud {
    PENDIENTE,   // Solicitud enviada al retirador, aún no ejecutada
    COMPLETADO   // Retiro confirmado → dinero descontado de cuentas COP, acreditado a caja
}
