package com.binance.web.BinanceAPI;
import lombok.Data;

@Data
public class AnuncioDto {
    private String cuenta;
    private String precio;
    private String moneda;
    private String fiat;
    private String minimo;
    private String maximo;
    private String metodoPago;
    private String vendedor;
    private String tipo;
    private String horaAnuncio;

}
