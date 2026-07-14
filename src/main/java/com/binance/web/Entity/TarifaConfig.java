package com.binance.web.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuración de la tarifa por hora que se le paga a los operadores.
 * Fila única (id = 1). Valor por defecto: 7500 COP/hora.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tarifa_config")
public class TarifaConfig {

    @Id
    private Integer id;

    /** Valor pagado por hora trabajada, en COP. */
    @Column(nullable = false)
    private Double valorHora;
}
