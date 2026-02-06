package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(
		  name = "buy_dollars",
		  uniqueConstraints = {
		    @UniqueConstraint(name = "uk_buy_dollars_dedupe_key", columnNames = "dedupe_key")
		  },
		  indexes = {
		    @Index(name = "idx_buy_dollars_date", columnList = "date")
		  }
		)
public class BuyDollars {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private Double tasa;
	private Double amount;
	private String cryptoSymbol;
	private Double pesos;
	@ManyToOne
	@JoinColumn(name = "supplier_id")
	private Supplier supplier;
	@ManyToOne
	@JoinColumn(name = "cliente_id")
	private Cliente cliente;
	private LocalDateTime date;
	private String nameAccount;
	private String idDeposit;
	@ManyToOne
	@JoinColumn(name = "account_binance_id")
	private AccountBinance accountBinance;
	private Boolean asignada;
	private Double saldoAnterior;
	
	  // 🔒 Clave de desduplicación SIEMPRE no-nula
	  @Column(name = "dedupe_key", nullable = false, length = 128)
	  private String dedupeKey;
	  @Column(name="tx_id", length=128)
	  private String txId;
	
}
