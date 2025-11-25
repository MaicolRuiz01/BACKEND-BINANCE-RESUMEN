package com.binance.web.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CryptoResumenDiaDto {
    private String cripto;   // "TRX"
    private Double saldo;    // saldoFinalCripto
    private Double tasa;     // tasaPromedioDia
    private Double usdt;     // saldo * tasa
}
