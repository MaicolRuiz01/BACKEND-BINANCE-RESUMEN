package com.binance.web.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CryptoPendienteDto {
    private String cripto;
    private Double saldoActualCripto;
}