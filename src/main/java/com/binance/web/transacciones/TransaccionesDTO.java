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
	private String txId;
	
	public static TransaccionesDTO fromEntity(Transacciones t) {
        return new TransaccionesDTO(
            t.getCantidad(),               // monto
            t.getIdtransaccion(),          // idtransaccion
            // null-safe: si la cuenta es externa (no registrada, ej. una wallet de Bybit),
            // cuentaTo/cuentaFrom pueden ser null → se deja null (el front lo muestra como "Externa").
            t.getCuentaTo() != null ? t.getCuentaTo().getName() : null,     // cuentaTo
            t.getCuentaFrom() != null ? t.getCuentaFrom().getName() : null, // cuentaFrom
            t.getFecha(),                  // fecha
            t.getTipo(),                    // tipo
            t.getTxId()
        );
    }
	

}
