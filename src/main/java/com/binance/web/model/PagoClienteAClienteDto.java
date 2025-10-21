package com.binance.web.model;

import lombok.Data;

@Data
public class PagoClienteAClienteDto {
    private Integer clienteOrigenId;
    private Integer clienteDestinoId;
    private Double usdt;          // requerido > 0
    private Double tasaOrigen;    // COP/USDT > 0
    private Double tasaDestino;   // COP/USDT > 0
    private String  nota;         // opcional
}

