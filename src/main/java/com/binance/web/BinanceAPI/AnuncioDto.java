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
    /** Cantidad disponible para negociar (ej: "150.00 USDT") */
    private String disponible;
    /** ID del anuncio en Binance */
    private String advNo;
}
