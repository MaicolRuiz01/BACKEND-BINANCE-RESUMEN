package com.binance.web.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuración de caché en memoria con Caffeine.
 *
 * Cachés disponibles:
 *  - trongridTrc20     : respuestas de TronGrid por wallet  (TTL 60 s)
 *  - binancePrices     : mapa de precios USDT de Binance    (TTL 90 s)
 *  - binanceServerTime : timestamp del servidor de Binance  (TTL 30 s)
 *  - binancePayHistory : historial BinancePay por cuenta    (TTL 120 s)
 */
@Configuration
public class CacheConfig {

    /** TTLs en segundos */
    private static final int TRONGRID_TTL       = 60;
    private static final int BINANCE_PRICES_TTL = 90;
    private static final int SERVER_TIME_TTL    = 30;
    private static final int PAYMENT_HIST_TTL   = 120;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Registra cada caché con su propio TTL usando builders individuales
        manager.registerCustomCache("trongridTrc20",
                Caffeine.newBuilder()
                        .expireAfterWrite(TRONGRID_TTL, TimeUnit.SECONDS)
                        .maximumSize(200)
                        .build());

        manager.registerCustomCache("binancePrices",
                Caffeine.newBuilder()
                        .expireAfterWrite(BINANCE_PRICES_TTL, TimeUnit.SECONDS)
                        .maximumSize(1)
                        .build());

        manager.registerCustomCache("binanceServerTime",
                Caffeine.newBuilder()
                        .expireAfterWrite(SERVER_TIME_TTL, TimeUnit.SECONDS)
                        .maximumSize(1)
                        .build());

        manager.registerCustomCache("binancePayHistory",
                Caffeine.newBuilder()
                        .expireAfterWrite(PAYMENT_HIST_TTL, TimeUnit.SECONDS)
                        .maximumSize(50)
                        .build());

        return manager;
    }
}
