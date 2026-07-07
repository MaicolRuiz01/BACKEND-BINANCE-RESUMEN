package com.binance.web.Entity;

public enum EstadoSolicitud {
    SIN_ASIGNAR, // Solicitud general publicada, esperando que un retirador la tome
    PENDIENTE,   // Asignada a un retirador, aún no ejecutada
    COMPLETADO,  // Retiro confirmado → dinero descontado de cuentas COP, acreditado a caja
    CANCELADO    // Cancelada manualmente desde la app (ej: se envió por error). No se movió dinero.
}
