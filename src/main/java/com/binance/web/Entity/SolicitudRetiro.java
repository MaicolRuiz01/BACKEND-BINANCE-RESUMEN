package com.binance.web.Entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "solicitud_retiro")
public class SolicitudRetiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null mientras la solicitud está SIN_ASIGNAR */
    @ManyToOne(optional = true)
    @JoinColumn(name = "retirador_id", nullable = true)
    private Retirador retirador;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    /** Suma total de dinero a retirar de todas las cuentas */
    @Column(name = "total_monto", nullable = false)
    private Double totalMonto = 0.0;

    /** Cuánto se le paga al retirador por esta solicitud */
    @Column(name = "pago_retirador", nullable = false)
    private Double pagoRetirador = 0.0;

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleRetiro> detalles = new ArrayList<>();

    /**
     * message_id del mensaje de Telegram enviado al grupo para esta solicitud,
     * usado para poder editarlo o borrarlo después.
     */
    @Column(name = "telegram_message_id", nullable = true)
    private Integer telegramMessageId;
}
