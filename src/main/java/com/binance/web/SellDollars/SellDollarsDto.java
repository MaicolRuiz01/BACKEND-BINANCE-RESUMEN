package com.binance.web.SellDollars;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellDollarsDto {
	private String idWithdrawals;
    private Double tasa;
    private Double dollars;
    private Double pesos;
    private Date date;
    private String nameAccount;

}
