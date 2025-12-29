package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompraVES {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Fecha/hora de la operación
     */
    @Column(name = "date", nullable = false)
    private LocalDateTime date;

    /**
     * Monto comprado en bolívares (VES)
     */
    @Column(name = "bolivares", nullable = false)
    private Double bolivares;

    /**
     * Tasa en COP por 1 VES
     * (por ejemplo: 1 VES = 120 COP)
     */
    @Column(name = "tasa", nullable = false)
    private Double tasa;

    /**
     * Total en pesos de la operación:
     *  pesos = bolivares * tasa
     */
    @Column(name = "pesos", nullable = false)
    private Double pesos;

    /**
     * Cliente asociado (opcional).
     * Ej: compra Bs para un cliente específico.
     */
    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    /**
     * Proveedor de bolívares (quien nos vende los Bs).
     */
    @ManyToOne
    @JoinColumn(name = "proveedor_id")
    private Supplier supplier;

    /**
     * Cuenta COP desde la que se paga esta compra
     * (si aplica).
     */
    @ManyToOne
    @JoinColumn(name = "cuenta_cop_id")
    private AccountCop cuentaCop;

}
