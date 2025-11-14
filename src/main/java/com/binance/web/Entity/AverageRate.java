package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AverageRate {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Momento exacto del cálculo
    private LocalDateTime fecha;

    // Tasa promedio vigente luego de ese cálculo
    private Double averageRate;

    // Saldo total interno en USDT al momento del cálculo
    private Double saldoTotalInterno;

    // ===== CAMPOS PARA LA LÓGICA DIARIA =====

    // Inicio del día al que pertenece este snapshot (00:00 de ese día, en Bogotá)
    private LocalDateTime inicioDia;

    // Saldo inicial del día en USDT
    private Double saldoInicialDia;

    // Tasa con la que se valora ese saldo inicial (tasa promedio del día anterior o inicial)
    private Double tasaBaseSaldoInicial;

    // Acumulado de compras del día en USDT
    private Double totalUsdtComprasDia;

    // Acumulado de compras del día en pesos
    private Double totalPesosComprasDia;
	

}
