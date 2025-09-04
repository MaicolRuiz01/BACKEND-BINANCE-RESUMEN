package com.binance.web.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class AccountBinanceDTO {
    private Integer id;
    private String name;
    private String referenceAccount;
    private String correo;
    private String userBinance;
    private String address;
    private String tipo;
    private Double balance; // saldo interno en USD
}
