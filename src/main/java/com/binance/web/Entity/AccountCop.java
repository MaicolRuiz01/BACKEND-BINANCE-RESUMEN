package com.binance.web.Entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "account_cop")
public class AccountCop {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	private String name;
	private Double balance;
	
	@OneToMany(mappedBy = "accountCop", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
	private List<SaleP2pAccountCop> saleP2pDetails;
	
	@OneToMany(mappedBy = "accountCop", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
    private List<SellDollarsAccountCop> sellDollars;
}
