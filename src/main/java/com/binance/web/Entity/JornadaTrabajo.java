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
 * Jornada de trabajo REAL de un operador (distinta de la sesión).
 * El operador oprime "Empecé a trabajar" (startedAt) y "Terminé" (endedAt).
 *
 * A diferencia de {@link SesionOperador} (que mide tiempo con la app abierta),
 * la jornada mide el tiempo por el que efectivamente se le paga.
 *
 * Duración = (endedAt si existe, si no ahora) - startedAt.
 * Una jornada con endedAt == null está EN CURSO.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "jornada_trabajo")
public class JornadaTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 64)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Rol rol;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** null mientras la jornada sigue en curso. */
    private LocalDateTime endedAt;
}
