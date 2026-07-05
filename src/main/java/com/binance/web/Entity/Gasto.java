package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "gasto")
public class Gasto {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private String descripcion;
	private LocalDateTime fecha;
	private Double monto;
	@ManyToOne
	private AccountCop cuentaPago;
	@ManyToOne
	private Efectivo pagoEfectivo;

	/**
	 * Clave de idempotencia: el frontend genera una por cada modal de "Nuevo gasto".
	 * Evita que clics repetidos (cuando la app parece no responder) creen gastos
	 * duplicados y resten el saldo varias veces. Es unique: el segundo intento con
	 * la misma clave no inserta un gasto nuevo.
	 */
	@Column(unique = true)
	private String idempotencyKey;

}
