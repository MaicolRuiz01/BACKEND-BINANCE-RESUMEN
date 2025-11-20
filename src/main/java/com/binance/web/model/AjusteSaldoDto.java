package com.binance.web.model;

import lombok.Data;

@Data
public class AjusteSaldoDto {

    // "CLIENTE", "PROVEEDOR", "CAJA", "CUENTACOP"
    private String   entidad;
    private Integer  entidadId;

    // 🔹 monto del ajuste (siempre positivo)
    private Double   monto;

    // 🔹 true = ENTRADA (suma), false = SALIDA (resta)
    private Boolean  entrada;

    private String   motivo;
    private String   actor;
}

