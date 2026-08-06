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
 * Si una cuenta propia tiene o no anuncio de venta publicado ahora mismo.
 *
 * Se guarda por CUENTA (nickname de Binance) y no por anuncio (advNo) a propósito: cuando el
 * operador apaga y vuelve a publicar, Binance le asigna un advNo nuevo, así que seguir el advNo
 * haría ver un "apagado + encendido" como dos anuncios distintos sin relación. Lo que le importa
 * al administrador es simplemente si esa cuenta está publicando o no.
 *
 * Va en BD y no en memoria porque si no, cada reinicio de Railway reportaría como "encendidos"
 * todos los anuncios existentes.
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cuenta_anuncio_estado")
public class CuentaAnuncioEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Nickname de Binance de la cuenta propia (campo userBinance). */
    @Column(unique = true, nullable = false, length = 120)
    private String vendedor;

    /** true = tenía anuncio publicado la última vez que se miró. */
    private Boolean activo;

    /** Última vez que se vio publicado (para el mensaje de "estuvo apagado X tiempo"). */
    private LocalDateTime ultimoVistoActivoAt;

    private LocalDateTime actualizadoAt;
}
