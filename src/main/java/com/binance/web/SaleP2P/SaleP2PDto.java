package com.binance.web.SaleP2P;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class SaleP2PDto {
	private Integer id;
	private String numberOrder;
	private LocalDateTime date;
	private Double commission;
	private Double pesosCop;
	private List<Integer> accountCopIds; 
	private String nameAccount;
	private String nameAccountBinance;
	private Map<Integer, Double> accountAmounts;
	private Double dollarsUs;
}
