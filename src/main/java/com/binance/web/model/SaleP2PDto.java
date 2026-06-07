package com.binance.web.model;

import java.time.LocalDateTime;
import java.util.List;

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
	private Double tasa;
	private Boolean asignado;
	private List<AccountCopDetailDto> accountCopsDetails;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class AccountCopDetailDto {
		private String nameAccount;
		private Double amount;
	}
}
