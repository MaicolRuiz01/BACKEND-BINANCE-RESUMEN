package com.binance.web.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "account_crypto_balance")
public class AccountCryptoBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String cryptoSymbol; // Por ejemplo: "USDT", "TRX", "USDC"
    private Double balance; // El valor del balance para esa criptomoneda

    // ✅ Relación Many-to-One con AccountBinance
    // Esta columna almacenará la clave foránea (id de la cuenta)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_binance_id", nullable = false)
    @JsonIgnore
    private AccountBinance accountBinance;
}
