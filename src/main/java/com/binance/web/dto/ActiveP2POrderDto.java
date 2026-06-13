package com.binance.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de una orden P2P activa (no completada aún).
 * Se construye en tiempo real desde Binance y se enriquece
 * con la pre-asignación guardada en BD si existe.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActiveP2POrderDto {

    private String orderNumber;
    private String status;          // TRADING, BUYER_PAYED, PENDING
    private String statusLabel;     // etiqueta legible en español
    private String accountBinance;
    private Double dollarsUs;
    private Double pesosCop;        // ya dividido entre 1000
    private Double tasa;
    private String createTime;      // ISO string

    /** null si aún no se pre-asignó */
    private Integer preAsignadoCopId;
    private String  preAsignadoCopNombre;
}
