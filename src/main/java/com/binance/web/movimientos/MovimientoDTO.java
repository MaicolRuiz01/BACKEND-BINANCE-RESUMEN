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
	private String cajaDestino;
	private String pagoCliente;
	private String pagoProveedor;
	
	private String motivo;
    private String actor;
    private Double saldoAnterior;
    private Double saldoNuevo;
    private Double diferencia;

    /** Constructor liviano para la proyección JPQL de movimientos de caja (evita el N+1 del EAGER). */
    public MovimientoDTO(Integer id, String tipo, LocalDateTime fecha, Double monto,
                         String cuentaOrigen, String cuentaDestino, String caja, String cajaDestino,
                         String pagoCliente, String pagoProveedor) {
        this.id = id;
        this.tipo = tipo;
        this.fecha = fecha;
        this.monto = monto;
        this.cuentaOrigen = cuentaOrigen;
        this.cuentaDestino = cuentaDestino;
        this.caja = caja;
        this.cajaDestino = cajaDestino;
        this.pagoCliente = pagoCliente;
        this.pagoProveedor = pagoProveedor;
    }

}
