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

@Data
@Entity
@Table(name= "sell_dollars_account_cop")
@AllArgsConstructor
@NoArgsConstructor
public class SellDollarsAccountCop {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
	
	private Double amount;

    private String nameAccount;

    @ManyToOne
    @JoinColumn(name = "sell_dollars_id")
    private SellDollars sellDollars;

    @ManyToOne
    @JoinColumn(name = "account_cop_id")
    private AccountCop accountCop;

}
