package com.binance.web.BuyDollars;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuyDollarsDto {

    private Double dollars;      // Monto de dólares a comprar
    private Double tasa;         // Tasa de cambio aplicada
    private String nameAccount;  // Nombre de la cuenta
    private Date date;           // Fecha de la operación
    private String idDeposit;
    private Double pesos;
    private Integer accountBinanceId;
	
}
