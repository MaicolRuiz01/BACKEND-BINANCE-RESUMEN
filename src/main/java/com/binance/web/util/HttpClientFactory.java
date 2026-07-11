package com.binance.web.util;

import java.time.Duration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Fábrica de RestTemplate CON timeouts.
 *
 * Un {@code new RestTemplate()} por defecto NO tiene timeout: si la API externa (Binance,
 * TronScan, Solscan, Telegram…) se cuelga o tarda demasiado, el hilo que hace la llamada
 * queda bloqueado indefinidamente. Con open-in-view activo ese hilo además retiene una
 * conexión del pool de la BD, así que unas pocas llamadas colgadas agotan el pool y la app
 * deja de poder cargar CUALQUIER cosa (proveedores, cajas, cuentas) hasta reiniciar.
 *
 * Con timeouts, una llamada lenta falla rápido (lanza excepción, que ya se captura/loguea)
 * y libera el hilo y la conexión, en vez de dejar la app "colgada pero Online".
 */
public final class HttpClientFactory {

    /** Tiempo máximo para ESTABLECER la conexión TCP con la API externa. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Tiempo máximo esperando la RESPUESTA una vez conectado. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private HttpClientFactory() {}

    /** RestTemplate con timeouts de conexión y lectura. Usar en vez de {@code new RestTemplate()}. */
    public static RestTemplate timed() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return new RestTemplate(factory);
    }
}
