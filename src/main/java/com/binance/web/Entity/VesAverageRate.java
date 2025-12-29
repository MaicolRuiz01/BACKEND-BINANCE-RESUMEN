package com.binance.web.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
public class VesAverageRate {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;
	@Column(name = "dia", nullable = false)
    private LocalDate dia;

    @Column(name = "fecha_calculo", nullable = false)
    private LocalDateTime fechaCalculo;

    @Column(name = "saldo_inicial_ves", nullable = false)
    private Double saldoInicialVes;

    @Column(name = "tasa_base_cop", nullable = false)
    private Double tasaBaseCop;

    @Column(name = "total_ves_comprados_dia", nullable = false)
    private Double totalVesCompradosDia;
    @Column(name = "total_pesos_compras_dia", nullable = false)
    private Double totalPesosComprasDia;

    @Column(name = "tasa_promedio_dia", nullable = false)
    private Double tasaPromedioDia;
    @Column(name = "saldo_final_ves", nullable = false)
    private Double saldoFinalVes;

}
