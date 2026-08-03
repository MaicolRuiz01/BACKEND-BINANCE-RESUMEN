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

    // ── Pausa automática del cronómetro ───────────────────────────
    // La jornada sigue ABIERTA pero el tiempo no corre (y por lo tanto no se paga).
    // El operador la reanuda desde la app cuando corrige lo que la disparó.

    /** Momento en que se pausó. null = el cronómetro está corriendo. */
    private LocalDateTime pausadaAt;

    /** Segundos ya acumulados en pausas anteriores de esta misma jornada. */
    private Long segundosPausados;

    /** Por qué se pausó (se le muestra al operador y se manda por Telegram). */
    @Column(length = 300)
    private String motivoPausa;

    // ── Estado de la vigilancia de anuncio y tasa ─────────────────

    /**
     * Primera vez en esta jornada que se vio un anuncio publicado de alguna cuenta propia.
     * Si sigue en null pasados los minutos de gracia, es que el operador nunca publicó
     * anuncio y se le pausa el cronómetro.
     */
    private LocalDateTime tuvoAnuncioAt;

    /** Cuándo se le avisó que le bajara un punto a la tasa (arranca la cuenta regresiva). */
    private LocalDateTime avisoTasaAt;

    /** Tasa que tenía el anuncio en el momento de ese aviso, para comparar si la bajó. */
    private Double tasaAlAvisar;

    /** Anuncio (advNo) sobre el que se hizo el aviso, para comparar contra el mismo. */
    @Column(length = 64)
    private String advNoAvisado;

    // ── Aviso pendiente para el operador ──────────────────────────

    /**
     * Mensaje que el operador todavía no ha visto. Se empuja al instante por SSE, pero
     * también queda acá para que la app lo recupere si el SSE se cayó (Railway) o si el
     * operador recarga la página. Se limpia cuando la app confirma que lo mostró.
     */
    @Column(length = 400)
    private String avisoPendiente;

    private LocalDateTime avisoPendienteAt;
}
