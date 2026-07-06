package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registro de una sesión de un operador/admin en la app.
 * - loginAt: cuándo inició sesión.
 * - lastSeenAt: último "heartbeat" recibido mientras la app estaba abierta.
 * - logoutAt: cuándo cerró sesión (null si cerró la pestaña sin cerrar sesión).
 *
 * Duración de la sesión = (logoutAt si existe, si no lastSeenAt) - loginAt.
 * Con el heartbeat cada minuto, lastSeenAt aproxima bien el fin real aunque
 * el usuario no oprima "cerrar sesión".
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "sesion_operador")
public class SesionOperador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 64)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Rol rol;

    @Column(nullable = false)
    private LocalDateTime loginAt;

    @Column(nullable = false)
    private LocalDateTime lastSeenAt;

    private LocalDateTime logoutAt;
}
