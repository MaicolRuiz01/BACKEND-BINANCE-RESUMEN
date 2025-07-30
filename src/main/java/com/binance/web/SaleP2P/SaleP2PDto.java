package com.binance.web.SaleP2P;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SaleP2PDto {
	private Integer id;
	private String numberOrder;
	private LocalDateTime date;
	private Double commission;
	private Double pesosCop;
	private String nameAccountBinance;
	private Double dollarsUs;
	private Double tasa;
}
