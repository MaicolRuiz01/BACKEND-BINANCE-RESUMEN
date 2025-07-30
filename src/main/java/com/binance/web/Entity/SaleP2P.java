package com.binance.web.Entity;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "sale_p2p")
public class SaleP2P {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private String numberOrder;
	private LocalDateTime date;
	private Double commission;
	private Double pesosCop;
	private Double dollarsUs;
	private Double tasa;

	@OneToMany(mappedBy = "saleP2p", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
	private List<SaleP2pAccountCop> accountCopsDetails;

	@ManyToOne
	@JoinColumn(name = "binance_account_id")
	private AccountBinance binanceAccount;

	private Double utilidad;
}
