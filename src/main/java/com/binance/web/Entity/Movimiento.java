package com.binance.web.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Movimiento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private String tipo;
	private LocalDateTime fecha;
	private Double monto;
	@ManyToOne
	private AccountCop cuentaOrigen;
	@ManyToOne
	private AccountCop cuentaDestino;
	@ManyToOne
	private Efectivo caja;
	private Double comision;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "cuenta_origen_id")
	private Cliente pagoCliente;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "pago_proveedor_id")
	private Supplier pagoProveedor;
	@JoinColumn(name = "proveedor")
	private String proveedor;

}
