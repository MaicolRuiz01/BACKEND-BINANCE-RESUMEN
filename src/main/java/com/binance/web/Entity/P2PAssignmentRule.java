package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Regla de auto-asignación P2P.
 *
 * Una fila por cuenta Binance. Indica a qué cuenta COP
 * deben ir automáticamente las nuevas ventas P2P al importarse.
 *
 * Si active = false la auto-asignación está pausada para esa cuenta.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "p2p_assignment_rule")
public class P2PAssignmentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Cuenta Binance a la que aplica la regla (una sola regla activa por cuenta). */
    @OneToOne
    @JoinColumn(name = "binance_account_id", unique = true, nullable = false)
    private AccountBinance binanceAccount;

    /** Cuenta COP destino actual para las ventas P2P. */
    @ManyToOne
    @JoinColumn(name = "cop_account_id", nullable = false)
    private AccountCop copAccount;

    /** Si false, las nuevas ventas NO se auto-asignan (modo manual). */
    @Column(nullable = false)
    private Boolean active = true;

    /** Última vez que se cambió la regla. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
