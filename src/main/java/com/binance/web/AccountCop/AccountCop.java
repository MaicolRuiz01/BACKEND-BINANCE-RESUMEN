package com.binance.web.AccountCop;

import java.util.List;

import com.binance.web.SaleP2P.SaleP2P;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name="account_cop")
public class AccountCop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private Double balance;

    @ManyToMany(mappedBy = "accountCops")  // Relación inversa con `SaleP2P`
    private List<SaleP2P> saleP2Ps;  // Lista de ventas asociadas a esta cuenta
}

