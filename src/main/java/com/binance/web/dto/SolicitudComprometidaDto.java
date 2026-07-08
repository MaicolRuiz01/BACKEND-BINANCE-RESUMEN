package com.binance.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** Un item del desglose de "dinero comprometido" de una cuenta: una solicitud puntual aún no confirmada. */
@Data
@AllArgsConstructor
public class SolicitudComprometidaDto {
    private Long solicitudId;
    private Double monto;
    private String estado; // SIN_ASIGNAR | PENDIENTE
    private LocalDateTime fechaCreacion;
    private String retiradorNombre; // null si es solicitud general aún sin asignar
}
