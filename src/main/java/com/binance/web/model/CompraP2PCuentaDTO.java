package com.binance.web.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Compra P2P vista desde una cuenta COP: datos de la compra + el monto asignado a esa cuenta. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompraP2PCuentaDTO {
    private Integer buyId;
    private String numberOrder;
    private LocalDateTime date;
    private Double tasa;
    private Double dollarsUs;
    private Double pesosCop;        // total de la compra
    private Double montoAsignado;   // parte asignada a ESTA cuenta COP
    private String binanceAccountName; // cuenta Binance de la compra
}
