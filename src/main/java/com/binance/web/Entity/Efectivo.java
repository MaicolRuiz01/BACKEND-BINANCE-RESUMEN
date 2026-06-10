package com.binance.web.Entity;

import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class Efectivo {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private Double saldo;
	private String name;
	@Column(name = "saldo_inicial_del_dia")
    private Double saldoInicialDelDia;

	// Relación inversa con Retirador
	@OneToOne(mappedBy = "efectivo", cascade = CascadeType.ALL, orphanRemoval = true)
	private Retirador retirador;

}
