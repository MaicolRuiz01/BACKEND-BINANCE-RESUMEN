package com.binance.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RankingRetiradorDto {
    private Long retiradorId;
    private String nombre;
    private Double totalRetirado;
    private int posicion;
}
