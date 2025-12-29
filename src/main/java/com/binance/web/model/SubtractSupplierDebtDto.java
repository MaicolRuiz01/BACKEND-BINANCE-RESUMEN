package com.binance.web.model;

import lombok.Data;

@Data
public class SubtractSupplierDebtDto {
	private Double pesosCop;
	private Integer supplierId;
	private Integer accountCopId;
}