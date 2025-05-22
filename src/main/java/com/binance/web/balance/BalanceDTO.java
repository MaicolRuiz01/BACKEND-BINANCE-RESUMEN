package com.binance.web.balance;

import java.time.LocalDate;

import lombok.Data;

@Data
public class BalanceDTO {
	private Integer id;
	private LocalDate date;
	private Double saldo;
}
