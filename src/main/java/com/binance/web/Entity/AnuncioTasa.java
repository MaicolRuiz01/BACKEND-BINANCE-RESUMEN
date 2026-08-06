package com.binance.web.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Última tasa vista de cada anuncio propio de Binance P2P.
 *
 * Sirve para detectar que un anuncio cambió de precio (subió o bajó) y avisarle al
 * administrador por Telegram. Se guarda en BD y no en memoria a propósito: si no,
 * cada reinicio de Railway haría que el primer ciclo viera "todo cambiado" o perdiera
 * un cambio real.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "anuncio_tasa")
public class AnuncioTasa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Identificador del anuncio en Binance. */
    @Column(name = "adv_no", unique = true, nullable = false, length = 64)
    private String advNo;

    /** Nickname de la cuenta propia dueña del anuncio. */
    @Column(length = 120)
    private String vendedor;

    /** Última tasa (COP por USDT) que se le vio. */
    private Double tasa;

    private LocalDateTime actualizadoAt;
}
