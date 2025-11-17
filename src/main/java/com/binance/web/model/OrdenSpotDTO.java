package com.binance.web.model;

//SpotOrderDTO.java
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.binance.web.Entity.SpotOrder;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdenSpotDTO {

    private Long idInterno;
    private Long idOrden;
    private String cuenta;
    private String simbolo;

    private String tipoOperacion;  // COMPRA / VENTA

    private String cripto;
    private Double cantidadCripto;
    private Double totalUsdt;
    private Double tasaUsdt;

    private Double comisionUsdt;
    private LocalDateTime fechaOperacion;

    private String detalleBinanceJson;

    public static OrdenSpotDTO fromEntity(SpotOrder o) {
        return OrdenSpotDTO.builder()
            .idInterno(o.getId())
            .idOrden(o.getIdOrdenBinance())
            .cuenta(o.getCuentaBinance() != null ? o.getCuentaBinance().getName() : null)
            .simbolo(o.getSimbolo())
            .tipoOperacion(o.getTipoOperacion())
            .cripto(o.getCripto())
            .cantidadCripto(o.getCantidadCripto())
            .totalUsdt(o.getTotalUsdt())
            .tasaUsdt(o.getTasaUsdt())
            .comisionUsdt(o.getComisionUsdt())
            .fechaOperacion(o.getFechaOperacion())
            .detalleBinanceJson(o.getDetalleBinanceJson())
            .build();
    }
}
