package com.binance.web.transacciones;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransaccionesDTO {
	
	private Double monto;
	private String idtransaccion;
	private String cuentaTo;
	private String cuentaFrom;
	private LocalDateTime fecha;
	private String tipo;
	

}
