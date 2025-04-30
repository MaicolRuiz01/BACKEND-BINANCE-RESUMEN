package com.binance.web.balance.saleP2P;

import lombok.Data;

@Data
public class BalanceSaleP2PDto {

	private Double Total;
	private Double vendidos;
	private Double tasaCompra;
	private Double tasaVenta;
	private Double ComisionUsdt;
	private Double impuestosCol;
}
