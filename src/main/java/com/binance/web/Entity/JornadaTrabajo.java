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

    /**
     * En qué está trabajando: vendiendo USDT o haciendo caja. Lo elige el operador al
     * iniciar la jornada y define qué vigilancia se le aplica (ver ModoJornada).
     * Las jornadas viejas (anteriores a esta función) quedan en null y no se vigilan.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ModoJornada modo;

    /**
     * Última vez que el vigilante vio ventas P2P en curso. Es la referencia para contar
     * los 5 minutos "en seco": si es null se cuenta desde startedAt.
     */
    private LocalDateTime ultimaVentaVistaAt;

    /**
     * Última vez que se mandó un aviso por Telegram de esta jornada. Sirve para no
     * repetir el mensaje más seguido de la cuenta (cada 5 min en venta, cada hora en caja).
     */
    private LocalDateTime ultimaAlertaAt;
}
