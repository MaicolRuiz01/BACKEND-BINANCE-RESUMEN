package com.binance.web.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransaccionesDTO {
    private String idtransaccion;
    private String cuentaFrom;
    private String cuentaTo;
    private Double monto;
    private String tipo;
    private String cryptoSymbol;
    private LocalDateTime fecha;
}
