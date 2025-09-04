package com.binance.web.model;

//SpotOrderDTO.java
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.binance.web.Entity.SpotOrder;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrdenSpotDTO {
    private Long idInterno;
    private Long idOrden;              // orderId Binance
    private String cuenta;             // nombre cuenta
    private String simbolo;            // TRXUSDT...
    private String lado;               // BUY/SELL
    private String estado;             // FILLED, CANCELED...
    private BigDecimal precioPromedio; // avgPrice (quote/base)
    private BigDecimal cantidadBase;   // executedBaseQty
    private BigDecimal cantidadCot;    // executedQuoteQty
    private BigDecimal comisionUsdt;   // feeTotalUsdt (aplanado a USDT)
    private LocalDateTime fechaEjec;   // filledAt (fecha real de ejecución)

    public static OrdenSpotDTO fromEntity(SpotOrder o) {
        return OrdenSpotDTO.builder()
            .idInterno(o.getId())
            .idOrden(o.getOrderId())
            .cuenta(o.getAccount()!=null? o.getAccount().getName(): null)
            .simbolo(o.getSymbol())
            .lado(o.getSide())
            .estado(o.getStatus())
            .precioPromedio(BigDecimal.valueOf(o.getAvgPrice()!=null? o.getAvgPrice(): 0.0))
            .cantidadBase(BigDecimal.valueOf(o.getExecutedBaseQty()!=null? o.getExecutedBaseQty(): 0.0))
            .cantidadCot(BigDecimal.valueOf(o.getExecutedQuoteQty()!=null? o.getExecutedQuoteQty(): 0.0))
            .comisionUsdt(BigDecimal.valueOf(o.getFeeTotalUsdt()!=null? o.getFeeTotalUsdt(): 0.0))
            .fechaEjec(o.getFilledAt())
            .build();
    }
}