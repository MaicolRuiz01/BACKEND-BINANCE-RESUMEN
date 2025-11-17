package com.binance.web.cryptoAverageRate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.Entity.CryptoAverageRate;
import com.binance.web.Repository.CryptoAverageRateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CryptoAverageRateServiceImpl implements CryptoAverageRateService {

    private final CryptoAverageRateRepository repo;
    private final AccountBinanceService accountBinanceService;

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    @Override
    public CryptoAverageRate getUltimaPorCripto(String cripto) {
        return repo.findTopByCriptoOrderByFechaCalculoDesc(cripto.toUpperCase()).orElse(null);
    }

    @Override
    public CryptoAverageRate inicializarCripto(String cripto, Double tasaInicialUsdt, LocalDateTime fecha) {
        String c = cripto.toUpperCase();

        // Solo si nunca se ha configurado esa cripto
        if (repo.findTopByCriptoOrderByFechaCalculoDesc(c).isPresent()) {
            throw new IllegalStateException("La cripto " + c + " ya tiene tasa inicial configurada.");
        }

        Double saldoInicialCripto = accountBinanceService
                .getTotalCryptoBalanceExterno(c);

        LocalDate dia = fecha.atZone(ZONE_BOGOTA).toLocalDate();

        CryptoAverageRate rate = new CryptoAverageRate();
        rate.setCripto(c);
        rate.setDia(dia);
        rate.setFechaCalculo(fecha);
        rate.setSaldoInicialCripto(saldoInicialCripto);
        rate.setTasaBaseUsdt(tasaInicialUsdt);
        rate.setTotalCriptoCompradaDia(0.0);
        rate.setTotalUsdtComprasDia(0.0);
        rate.setTasaPromedioDia(tasaInicialUsdt);
        rate.setSaldoFinalCripto(saldoInicialCripto);

        return repo.save(rate);
    }

    @Override
    public CryptoAverageRate actualizarPorCompra(
            String cripto,
            Double cantidadCriptoComprada,
            Double totalUsdtCompra,
            LocalDateTime fechaOperacion
    ) {
        String c = cripto.toUpperCase();
        LocalDate dia = fechaOperacion.atZone(ZONE_BOGOTA).toLocalDate();

        CryptoAverageRate ultima = repo.findTopByCriptoOrderByFechaCalculoDesc(c)
                .orElseThrow(() -> new IllegalStateException(
                        "Primero debes configurar la tasa inicial para la cripto " + c));

        // saldo interno actual de esa cripto (incluyendo esta compra)
        Double saldoTotalActualCripto = accountBinanceService
                .getTotalCryptoBalanceInterno(c);

        CryptoAverageRate snapshotDia = repo.findByCriptoAndDia(c, dia).orElse(null);

        if (snapshotDia == null) {
            // 🔹 Primera compra del día para esa cripto

            // saldo inicial del día = saldo actual - compra actual
            Double saldoInicialDia = saldoTotalActualCripto - cantidadCriptoComprada;

            Double usdtInicial = saldoInicialDia * ultima.getTasaPromedioDia();

            Double totalCriptoDia = saldoInicialDia + cantidadCriptoComprada;
            Double totalUsdtDia   = usdtInicial + totalUsdtCompra;

            Double nuevaTasa = totalCriptoDia > 0 ? totalUsdtDia / totalCriptoDia : 0.0;

            snapshotDia = new CryptoAverageRate();
            snapshotDia.setCripto(c);
            snapshotDia.setDia(dia);
            snapshotDia.setFechaCalculo(fechaOperacion);
            snapshotDia.setSaldoInicialCripto(saldoInicialDia);
            snapshotDia.setTasaBaseUsdt(ultima.getTasaPromedioDia());
            snapshotDia.setTotalCriptoCompradaDia(cantidadCriptoComprada);
            snapshotDia.setTotalUsdtComprasDia(totalUsdtCompra);
            snapshotDia.setTasaPromedioDia(nuevaTasa);
            snapshotDia.setSaldoFinalCripto(saldoTotalActualCripto);

        } else {
            // 🔹 Segunda, tercera, ... compra del mismo día

            Double saldoInicialDia = snapshotDia.getSaldoInicialCripto();
            Double tasaBase = snapshotDia.getTasaBaseUsdt();

            Double usdtInicial = saldoInicialDia * tasaBase;

            Double totalCriptoComprasDia = snapshotDia.getTotalCriptoCompradaDia() + cantidadCriptoComprada;
            Double totalUsdtComprasDia   = snapshotDia.getTotalUsdtComprasDia() + totalUsdtCompra;

            Double totalCriptoDia = saldoInicialDia + totalCriptoComprasDia;
            Double totalUsdtDia   = usdtInicial + totalUsdtComprasDia;

            Double nuevaTasa = totalCriptoDia > 0 ? totalUsdtDia / totalCriptoDia : 0.0;

            snapshotDia.setFechaCalculo(fechaOperacion);
            snapshotDia.setTotalCriptoCompradaDia(totalCriptoComprasDia);
            snapshotDia.setTotalUsdtComprasDia(totalUsdtComprasDia);
            snapshotDia.setTasaPromedioDia(nuevaTasa);
            snapshotDia.setSaldoFinalCripto(saldoTotalActualCripto);
        }

        return repo.save(snapshotDia);
    }
}

