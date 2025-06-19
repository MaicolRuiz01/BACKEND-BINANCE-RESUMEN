package com.binance.web.Entity;

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

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "sale_p2p_account_cop")
public class SaleP2pAccountCop {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private Double amount;
	private String nameAccount;

	@ManyToOne
	@JoinColumn(name = "account_cop_id", nullable = true) 
	private AccountCop accountCop;

	@ManyToOne
	@JoinColumn(name = "sale_p2p_id")
	private SaleP2P saleP2p;
}
