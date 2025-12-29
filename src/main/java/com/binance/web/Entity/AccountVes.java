package com.binance.web.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name="account_ves")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountVes {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private Double balance;
	private String name;
	@Column(name = "saldo_inicial_del_dia")
    private Double saldoInicialDelDia;

}
