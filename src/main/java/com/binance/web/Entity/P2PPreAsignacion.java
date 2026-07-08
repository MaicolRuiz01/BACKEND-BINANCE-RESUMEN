package com.binance.web.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Pre-asignación de cuenta COP a una venta P2P que aún está en curso.
 *
 * Cuando el operador ve una orden en TRADING o BUYER_PAYED, puede
 * indicar de antemano a qué cuenta COP irá el dinero cuando se complete.
 *
 * Al hacer el sync y encontrar esa orden como COMPLETED, P2PSyncService
 * consulta esta tabla primero y usa la cuenta aquí indicada en vez de
 * la regla general.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "p2p_pre_asignacion")
public class P2PPreAsignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número de orden Binance P2P — único por fila. */
    @Column(name = "order_number", unique = true, nullable = false)
    private String orderNumber;

    /** Cuenta COP destino seleccionada por el operador. */
    @ManyToOne
    @JoinColumn(name = "cuenta_cop_id", nullable = false)
    private AccountCop cuentaCop;

    /** Nombre de la cuenta Binance de origen (para referencia). */
    @Column(name = "account_binance", nullable = false)
    private String accountBinance;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Estado manual del dinero de esta orden (lo controla el operario con 2 botones):
     *  - "PENDIENTE": aún no ha caído → cuenta en el saldo AMARILLO.
     *  - "RECIBIDO":  ya cayó → cuenta en el saldo VERDE (solo visual, no toca el saldo real).
     * Sirve porque el estado de Binance no siempre refleja si el pago llegó de verdad.
     */
    @Column(name = "estado_manual")
    private String estadoManual;
}
