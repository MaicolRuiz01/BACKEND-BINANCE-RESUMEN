package com.binance.web.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Datos de una deducción, de ida y de vuelta entre el frontend y la API. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeduccionDto {

    private Integer id;

    private Integer accountBinanceId;
    private String  accountBinanceNombre;

    private Integer accountCopId;
    private String  accountCopNombre;

    private Double dollarsUs;
    private Double tasa;
    private Double pesosCop;

    private LocalDateTime fecha;
    private String nota;

    /** Solo se manda al crear: evita duplicados por doble clic. */
    private String idempotencyKey;
}
