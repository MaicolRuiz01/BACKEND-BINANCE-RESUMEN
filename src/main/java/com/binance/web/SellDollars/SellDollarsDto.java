package com.binance.web.SellDollars;

import java.time.LocalDateTime;
import java.util.List;

import com.binance.web.SaleP2P.AssignAccountDto;

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
    private LocalDateTime date;
    private String nameAccount;
    private Integer accountBinanceId;
    //esto solo sera para cuando haya una orden trade de TRX
    private Double equivalenteciaTRX;
    private Integer supplier;
    
    private List<AssignAccountDto> accounts;

}
