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

    /** Total de este detalle = montoCajero + montoCorresponsal */
    public double totalDetalle() {
        double t = 0;
        if (montoCajero != null)      t += montoCajero;
        if (montoCorresponsal != null) t += montoCorresponsal;
        return t;
    }
}
