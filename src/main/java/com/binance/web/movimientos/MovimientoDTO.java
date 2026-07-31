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

    private Double saldoCajaResultante;
    private Double saldoCajaDestinoResultante;

    /** ID de la cuenta COP de origen/destino, para mostrar "#N" junto al nombre en la app. */
    private Integer cuentaOrigenId;
    private Integer cuentaDestinoId;

    /** Constructor liviano para la proyección JPQL de movimientos de caja (evita el N+1 del EAGER). */
    public MovimientoDTO(Integer id, String tipo, LocalDateTime fecha, Double monto,
                         String cuentaOrigen, String cuentaDestino, String caja, String cajaDestino,
                         String pagoCliente, String pagoProveedor, String motivo,
                         Double saldoCajaResultante, Double saldoCajaDestinoResultante,
                         Integer cuentaOrigenId, Integer cuentaDestinoId) {
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
        this.motivo = motivo;
        this.saldoCajaResultante = saldoCajaResultante;
        this.saldoCajaDestinoResultante = saldoCajaDestinoResultante;
        this.cuentaOrigenId = cuentaOrigenId;
        this.cuentaDestinoId = cuentaDestinoId;
    }

}
