package com.binance.web.Entity;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
    name = "sale_p2p",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_sale_p2p_number_order", columnNames = "number_order")
    }
)
public class SaleP2P {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "number_order", nullable = false, unique = true, length = 64)
    private String numberOrder;

    /** Hora en que Binance CREÓ la orden (no cuándo se registró acá). */
    private LocalDateTime date;

    /**
     * Hora en que ESTE sistema registró la venta y sumó su plata a la cuenta COP.
     *
     * Sin este dato no había forma de medir el retraso de la importación: date es la hora de
     * creación en Binance, así que para saber cuándo se acreditó una venta tocaba deducirlo del
     * orden de los id. Con esta columna se ve directo cuánto tardó cada venta en caer al saldo.
     */
    @Column(name = "importado_en")
    private LocalDateTime importadoEn;

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

    // ✅ como SellDollars
    private Boolean asignado = false;
}

