package com.binance.web.balanceGeneral;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BalanceGeneral {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	@Column(nullable = false)
    private LocalDate date;

    private Double saldo; // saldo total del día
    private Double tasaCompra;
    private Double tasaVenta;

    private Double totalP2P;
    private Double comisionesP2P;
    private Double totalGeneralSales;

}
