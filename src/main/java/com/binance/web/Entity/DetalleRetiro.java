package com.binance.web.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "detalle_retiro")
public class DetalleRetiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "solicitud_id")
    private SolicitudRetiro solicitud;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cuenta_cop_id")
    private AccountCop cuentaCop;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_retiro", nullable = false)
    private TipoRetiro tipoRetiro;

    /** Monto retirado por cajero (aplica si tipo = CAJERO o COMPLETO) */
    @Column(name = "monto_cajero")
    private Double montoCajero;

    /** Monto retirado por corresponsal (aplica si tipo = CORRESPONSAL o COMPLETO) */
    @Column(name = "monto_corresponsal")
    private Double montoCorresponsal;

    /**
     * Monto REAL de cajero, solo si el retirador confirmó una cifra distinta a
     * la solicitada (botón "Otra cifra" en Telegram: el corresponsal/cajero no
     * le dejó sacar exactamente lo pedido). Null = se retiró justo lo solicitado.
     */
    @Column(name = "monto_cajero_real")
    private Double montoCajeroReal;

    /** Igual que {@link #montoCajeroReal} pero para el canal corresponsal. */
    @Column(name = "monto_corresponsal_real")
    private Double montoCorresponsalReal;

    /** Total de este detalle = montoCajero + montoCorresponsal (lo SOLICITADO). */
    public double totalDetalle() {
        double t = 0;
        if (montoCajero != null)      t += montoCajero;
        if (montoCorresponsal != null) t += montoCorresponsal;
        return t;
    }

    /** Monto de cajero a usar al confirmar: el real si se registró uno distinto, si no el solicitado. */
    public double montoCajeroFinal() {
        if (montoCajeroReal != null) return montoCajeroReal;
        return montoCajero != null ? montoCajero : 0.0;
    }

    /** Monto de corresponsal a usar al confirmar: el real si se registró uno distinto, si no el solicitado. */
    public double montoCorresponsalFinal() {
        if (montoCorresponsalReal != null) return montoCorresponsalReal;
        return montoCorresponsal != null ? montoCorresponsal : 0.0;
    }

    /** Total FINAL de este detalle (real si se registró, si no el solicitado) — es lo que efectivamente se mueve al confirmar. */
    public double totalDetalleFinal() {
        return montoCajeroFinal() + montoCorresponsalFinal();
    }

    /** true si el retirador registró, al confirmar, un monto distinto al que se le había solicitado. */
    public boolean tieneMontoReal() {
        return montoCajeroReal != null || montoCorresponsalReal != null;
    }
}
