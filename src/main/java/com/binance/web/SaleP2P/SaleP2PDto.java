package com.binance.web.SaleP2P;

import java.util.Date;
import lombok.Data;

@Data
public class SaleP2PDto {
	private Integer id;
	private String numberOrder;
	private Date date;
	private String taxType;
	private Double pesosCop;
	private Integer accountCopId;
	private String nameAccount;
	private String nameAccountBinance;
}
