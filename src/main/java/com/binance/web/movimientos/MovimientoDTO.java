package com.binance.web.movimientos;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MovimientoDTO {

	private Integer id;
	private String tipo;
	private LocalDateTime fecha;
	private Double monto;
	private String cuentaOrigen;
	private String cuentaDestino;
	private String caja;
	private String pagoCliente;
	private String pagoProveedor;
	
	private String motivo;
    private String actor;
    private Double saldoAnterior;
    private Double saldoNuevo;
    private Double diferencia;

}
