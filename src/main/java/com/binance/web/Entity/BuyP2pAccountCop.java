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
@Table(name = "buy_p2p_account_cop")
public class BuyP2pAccountCop {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Double amount;
    private String nameAccount;

    @ManyToOne
    @JoinColumn(name = "account_cop_id")
    private AccountCop accountCop; // puede ser null si es externa

    @ManyToOne
    @JoinColumn(name = "buy_p2p_id")
    private BuyP2P buyP2p;

}
