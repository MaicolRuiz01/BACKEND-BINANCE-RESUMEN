package com.binance.web.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class BuyP2PDto {
	
	private Integer id;
    private String numberOrder;
    private LocalDateTime date;
    private Double commission;
    private Double pesosCop;
    private Double dollarsUs;
    private Double tasa;
    private String nameAccountBinance;
    private Boolean asignado;

}
