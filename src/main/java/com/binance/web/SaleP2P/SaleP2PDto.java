package com.binance.web.SaleP2P;

import java.util.Date;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class SaleP2PDto {
	private String numberOrder;
	private Date date;
	private String taxType;
	private Double pesosCop;
	private List<Integer> accountCopIds; 
	private String nameAccount;
	private String nameAccountBinance;
	private Map<Integer, Double> accountAmounts; 
}
