package com.binance.web.movimientos;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MovimientoDTO {
	
	private String tipo;
	private LocalDateTime fecha;
	private Double monto;
	private String cuentaOrigen;
	private String cuentaDestino;
	private String caja;
	private String pagoCliente;

}
