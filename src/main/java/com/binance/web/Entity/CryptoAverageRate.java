package com.binance.web.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
public class CryptoAverageRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Qué cripto es: TRX, BNB, BTC...
    private String cripto;

    // Día al que corresponde este snapshot (por cripto)
    private LocalDate dia;

    // Momento exacto del cálculo
    private LocalDateTime fechaCalculo;

    // ===== SALDOS Y TASA =====

    // Saldo inicial del día en esa cripto (antes de las compras del día)
    private Double saldoInicialCripto;

    // Tasa con la que se valora ese saldo inicial (promedio del día anterior o inicial)
    private Double tasaBaseUsdt;  // USDT por 1 cripto

    // Acumulado de compras del día (solo COMPRA spot) en cripto
    private Double totalCriptoCompradaDia;

    // Acumulado de compras del día en USDT
    private Double totalUsdtComprasDia;

    // Tasa promedio resultante del día (USDT/cripto)
    private Double tasaPromedioDia;

    // Saldo final estimado en cripto al cierre del día (para usar como inicio del siguiente)
    private Double saldoFinalCripto;
}

