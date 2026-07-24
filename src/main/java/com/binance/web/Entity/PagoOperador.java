package com.binance.web.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pago que se le hace a un operador por su jornada de un día.
 *
 * El monto = (segundos trabajados ese día / 3600) * tarifa por hora. Al registrarlo se genera
 * además un {@link Gasto} ("Pago a operador X") que resta de la cuenta COP o caja elegida.
 * Sirve para el historial de pagos por operador y para bloquear el doble pago del mismo día.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pago_operador")
public class PagoOperador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Usuario del operador al que se le pagó. */
    @Column(nullable = false, length = 64)
    private String username;

    /** Día trabajado que se está pagando (para bloquear pagar dos veces el mismo día). */
    @Column(nullable = false)
    private LocalDate dia;

    /** Momento en que se registró el pago. */
    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Segundos trabajados ese día (base del cálculo). */
    private Long segundos;

    /** Tarifa por hora aplicada al momento del pago. */
    private Double tarifaHora;

    /** Monto pagado en COP. */
    private Double monto;

    /** Id del Gasto generado (para trazabilidad). */
    private Integer gastoId;

    /** De dónde salió el dinero, legible para el historial (ej. "Cuenta: Javier" o "Caja: Principal"). */
    private String origen;
}
