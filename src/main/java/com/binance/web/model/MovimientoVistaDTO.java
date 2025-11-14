package com.binance.web.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MovimientoVistaDTO {
	
	private Integer id;
    private String  tipo;
    private LocalDateTime fecha;

    // monto en COP listo para mostrar con signo (positivo=entrada / negativo=salida)
    private Double montoSigned;

    // opcional: flags útiles para UI
    private boolean entrada;  // true si montoSigned > 0
    private boolean salida;   // true si montoSigned < 0

    // opcional: texto auxiliar
    private String detalle;

}
