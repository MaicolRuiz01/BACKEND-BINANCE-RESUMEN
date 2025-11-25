package com.binance.web.cryptoAverageRate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.binance.web.Entity.CryptoAverageRate;
import com.binance.web.model.CryptoPendienteDto;

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
    List<CryptoPendienteDto> listarCriptosPendientesInicializacion();

    List<CryptoAverageRate> listarPorDia(LocalDate hoy);

}

