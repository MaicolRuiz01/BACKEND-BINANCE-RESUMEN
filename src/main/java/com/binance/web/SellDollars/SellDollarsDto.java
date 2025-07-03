package com.binance.web.SellDollars;

import java.time.LocalDateTime;
import java.util.List;

import com.binance.web.SaleP2P.AssignAccountDto;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellDollarsDto {
	private String idWithdrawals;
    private Double tasa;
    private Double dollars;
    private Double pesos;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "America/Bogota")
    private LocalDateTime date;
    
    private String nameAccount;
    private Integer accountBinanceId;
    //esto solo sera para cuando haya una orden trade de TRX
    private Double equivalenteciaTRX;
    private Integer supplier;
    
    private List<AssignAccountDto> accounts;

}
