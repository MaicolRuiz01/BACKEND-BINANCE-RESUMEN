package com.binance.web.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CryptoBalanceDto {
 private String symbol;
 private Double quantity;   // cantidad de la cripto
 private Double usdtValue;  // cantidad * precio en USDT
}

