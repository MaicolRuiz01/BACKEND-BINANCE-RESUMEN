package com.binance.web.Entity;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name="sale_p2p")
public class SaleP2P {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String numberOrder;
    private LocalDateTime date;
    private Double commission;
    private Double pesosCop;
    private Double dollarsUs;

    @ManyToMany
    @JoinTable(
        name = "sale_p2p_account_cop",  // Nombre de la tabla intermedia
        joinColumns = @JoinColumn(name = "sale_p2p_id"),  // Columna de la venta
        inverseJoinColumns = @JoinColumn(name = "account_cop_id")  // Columna de las cuentas COP
    )
    @JsonIgnore
    private List<AccountCop> accountCops;  // Cambié de "accountCop" a una lista de cuentas
    private String nameAccount;
    @ManyToOne
    @JoinColumn(name = "binance_account_id")
    private AccountBinance binanceAccount;
    private Double utilidad;

}

