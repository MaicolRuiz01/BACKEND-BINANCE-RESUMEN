package com.binance.web.cryptoAverageRate;

import java.time.LocalDateTime;

import com.binance.web.Entity.CryptoAverageRate;

public interface CryptoAverageRateService {

    CryptoAverageRate getUltimaPorCripto(String cripto);   // nullable

    CryptoAverageRate inicializarCripto(
            String cripto,
            Double tasaInicialUsdt,
            LocalDateTime fecha
    );

    CryptoAverageRate actualizarPorCompra(
            String cripto,
            Double cantidadCriptoComprada,
            Double totalUsdtCompra,
            LocalDateTime fechaOperacion
    );
}

