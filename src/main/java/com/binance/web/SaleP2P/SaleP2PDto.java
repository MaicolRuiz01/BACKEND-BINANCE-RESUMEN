package com.binance.web.SaleP2P;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleP2PDto {
	private Integer id;
	private String numberOrder;
	private LocalDateTime date;
	private Double commission;
	private Double pesosCop;
	private String nameAccountBinance;
	private Double dollarsUs;
}
