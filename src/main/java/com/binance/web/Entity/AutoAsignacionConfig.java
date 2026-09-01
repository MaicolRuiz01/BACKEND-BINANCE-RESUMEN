package com.binance.web.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Interruptor global de la asignación automática de cuentas COP a las ventas P2P en curso.
 * Fila única (id = 1). Por defecto apagado.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "auto_asignacion_config")
public class AutoAsignacionConfig {

    @Id
    private Integer id;

    /** true = el sistema asigna cuentas COP solo a las ventas en curso que van apareciendo. */
    @Column(nullable = false)
    private Boolean activa = false;
}
