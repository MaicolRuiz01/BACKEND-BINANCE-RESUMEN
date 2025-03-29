package com.binance.web.OrderP2P;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

@Data
public class OrderP2PDto {
		private String orderNumber;
	    private String tradeType;
	    private Double amount;
	    private Double totalPrice;
	    private Double unitPrice;
	    private String orderStatus;
	    
	    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy HH:mm:ss", timezone = "America/Bogota")
	    private Date createTime;
	    
	    private Double commission;
	    private String counterPartNickName;
	    private String payMethodName;
	    private String nameAccount;
	    private Double accountAmount;
}
