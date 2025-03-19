package com.binance.web.model.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

@Data
public class OrderP2PDto {
		private String orderNumber;
	    private String tradeType;
	    private String amount;
	    private String totalPrice;
	    private String unitPrice;
	    private String orderStatus;
	    
	    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy HH:mm:ss", timezone = "America/Bogota")
	    private Date createTime;
	    
	    private String commission;
	    private String counterPartNickName;
	    private String payMethodName;
	    private String nameAccount;
	    private Double accountAmount;
}
