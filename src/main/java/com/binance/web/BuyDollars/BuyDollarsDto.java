package com.binance.web.BuyDollars;

import java.util.Date;

import lombok.Data;

@Data
public class BuyDollarsDto {
	private Double tasa;
	private Double dollars;
	private Integer supplierId;
	private Integer accountBinanceId;
	private Date date;
}
