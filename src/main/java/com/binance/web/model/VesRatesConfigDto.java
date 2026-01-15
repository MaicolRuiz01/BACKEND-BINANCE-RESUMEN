package com.binance.web.model;

import lombok.Data;

@Data
public class VesRatesConfigDto {
    private Double ves1;
    private Double tasa1;

    private Double ves2;
    private Double tasa2;

    private Double ves3;
    private Double tasa3;

    // opcional: si lo mandas desde front
    private Boolean tasaUnica;
}