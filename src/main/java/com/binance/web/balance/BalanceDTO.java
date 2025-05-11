package com.binance.web.balance;

import java.util.Date;

import lombok.Data;

@Data
public class BalanceDTO {
	private Integer id;
	private Date date;
	private Double saldo;
}
