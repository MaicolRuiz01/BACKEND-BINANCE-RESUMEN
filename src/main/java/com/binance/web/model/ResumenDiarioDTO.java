package com.binance.web.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumenDiarioDTO {

    private Double entradasHoy;
    private Double salidasHoy;
    private Double ajustesHoy;
    private Double comprasDolaresHoy;
    private Double ventasDolaresHoy;
    private Double salidasRetirosHoy;
}
