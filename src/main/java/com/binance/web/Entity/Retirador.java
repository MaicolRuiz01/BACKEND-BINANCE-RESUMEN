package com.binance.web.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "retirador")
public class Retirador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    /** Caja (efectivo) a la que ingresa el dinero retirado */
    @OneToOne
    @JoinColumn(name = "efectivo_id", unique = true)
    private Efectivo efectivo;

    /**
     * Saldo acumulado que se le debe al retirador por su trabajo
     * (suma de pagoRetirador de todas las solicitudes completadas).
     */
    @Column(name = "saldo_pendiente", nullable = false)
    private Double saldoPendiente = 0.0;
}
