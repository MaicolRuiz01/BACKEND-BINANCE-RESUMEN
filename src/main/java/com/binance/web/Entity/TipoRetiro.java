package com.binance.web.Entity;

public enum TipoRetiro {
    CAJERO,       // Solo cajero automático → pago 2.000 COP
    CORRESPONSAL, // Solo corresponsal     → pago 3.000 COP
    COMPLETO      // Cajero + corresponsal → pago 4.000 COP
}
