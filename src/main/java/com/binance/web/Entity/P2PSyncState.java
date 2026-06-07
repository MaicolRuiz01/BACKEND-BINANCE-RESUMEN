package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Guarda el timestamp de la última sincronización P2P por cuenta Binance.
 *
 * Permite hacer queries delta: en vez de pedir TODAS las órdenes del día,
 * solo pedimos las que llegaron desde `lastSyncAtMs`.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "p2p_sync_state")
public class P2PSyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "binance_account_id", unique = true, nullable = false)
    private AccountBinance binanceAccount;

    /** Epoch millis — se pasa directo a Binance como startTimestamp. */
    @Column(name = "last_sync_at_ms")
    private Long lastSyncAtMs;

    /** Fecha/hora legible de la última sync (zona Bogotá). */
    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;
}
