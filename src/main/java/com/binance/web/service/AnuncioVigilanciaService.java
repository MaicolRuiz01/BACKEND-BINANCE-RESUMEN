package com.binance.web.service;

import java.util.List;

import com.binance.web.BinanceAPI.AnuncioDto;

/**
 * Fuente única de verdad sobre los anuncios de VENTA propios, para la vigilancia de jornadas.
 *
 * Existe para que todas las reglas (hay anuncio / cuál es la tasa / cambió la tasa) trabajen
 * sobre la MISMA foto y con UNA sola consulta a Binance por ciclo, en vez de que cada regla
 * llame por su cuenta y multiplique las peticiones.
 */
public interface AnuncioVigilanciaService {

    /** Anuncios de venta propios, de la foto cacheada (se refresca sola cuando vence). */
    List<AnuncioDto> anunciosVenta();

    /** ¿Hay al menos un anuncio de venta publicado de alguna cuenta propia? */
    boolean hayAnuncioPublicado();

    /**
     * ¿La última lectura de Binance fue confiable? Si Binance falló, las reglas que castigan
     * (pausar el cronómetro) deben abstenerse: un error de red no puede costarle el sueldo
     * a un operador que sí tiene el anuncio publicado.
     */
    boolean datosConfiables();

    /** Tasa de referencia: la del anuncio de venta más barato (el que compite por las órdenes). */
    Double tasaReferencia();

    /** advNo del anuncio de referencia, para comparar siempre contra el mismo. */
    String advNoReferencia();

    /** Tasa actual de un anuncio concreto; null si ya no está publicado. */
    Double tasaDe(String advNo);

    /**
     * Compara la foto actual contra la última tasa guardada de cada anuncio y avisa por
     * Telegram los que cambiaron. Devuelve cuántos cambios detectó.
     */
    int detectarYReportarCambiosDeTasa();

    /**
     * Detecta que una cuenta ENCENDIÓ o APAGÓ su anuncio de venta y lo avisa por Telegram.
     * Es lo que le permite al administrador enterarse de que alguien bajó el anuncio, que es
     * la razón más común de que dejen de entrar ventas.
     * Devuelve cuántos cambios de estado detectó.
     */
    int detectarYReportarAnunciosEncendidoApagado();
}
