package com.binance.web.movimientos;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MovimientoUpdateDTO {
    private Double monto;
    private Integer cuentaOrigenId;
    private Integer cuentaDestinoId;
    private Integer cajaId;

}
