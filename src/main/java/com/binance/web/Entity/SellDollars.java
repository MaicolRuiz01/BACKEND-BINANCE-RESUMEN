package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor
@Data
@NoArgsConstructor
@Table(name="sell_dollars")
public class SellDollars {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	@Column(name="id_withdrawals")
	private String idWithdrawals;
	private Double tasa;
	private Double dollars;
	private Double pesos;
	private LocalDateTime date;
	private String nameAccount;
	@ManyToOne
	@JoinColumn(name = "account_binance_id")  // La columna que hace referencia a account_binance
	private AccountBinance accountBinance;
	@ManyToOne
	@JoinColumn(name = "supplier_id")
	private Supplier supplier;

}
