package com.binance.web.transacciones;

import java.time.LocalDateTime;

import com.binance.web.Entity.Transacciones;

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
	
	public static TransaccionesDTO fromEntity(Transacciones t) {
        return new TransaccionesDTO(
            t.getCantidad(),               // monto
            t.getIdtransaccion(),          // idtransaccion
            t.getCuentaTo().getName(),     // cuentaTo
            t.getCuentaFrom().getName(),   // cuentaFrom
            t.getFecha(),                  // fecha
            t.getTipo()                    // tipo
        );
    }
	

}
