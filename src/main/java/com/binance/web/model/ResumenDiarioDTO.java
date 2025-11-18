package com.binance.web.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumenDiarioDTO {
    private Double comprasHoy; // entradas
    private Double ventasHoy;  // salidas
    private Double ajustesHoy; // ajustes de saldo
}
