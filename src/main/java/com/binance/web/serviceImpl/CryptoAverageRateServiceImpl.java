package com.binance.web.serviceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.Entity.CryptoAverageRate;
import com.binance.web.Repository.AccountCryptoBalanceRepository;
import com.binance.web.Repository.CryptoAverageRateRepository;
import com.binance.web.model.CryptoPendienteDto;
import com.binance.web.service.AccountBinanceService;
import com.binance.web.service.CryptoAverageRateService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CryptoAverageRateServiceImpl implements CryptoAverageRateService {

    private final CryptoAverageRateRepository repo;
    private final AccountBinanceService accountBinanceService;
    private final AccountCryptoBalanceRepository accountCryptoBalanceRepository;
    private final BinanceService binanceService; 

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    @Override
    public CryptoAverageRate getUltimaPorCripto(String cripto) {
        return repo.findTopByCriptoOrderByFechaCalculoDesc(cripto.toUpperCase()).orElse(null);
    }

    @Override
    @Transactional
    public void inicializarCriptosDelDia(LocalDateTime fecha) {
        LocalDate dia = fecha.atZone(ZONE_BOGOTA).toLocalDate();

        // Si ya existen snapshots de hoy, no hacemos nada para evitar duplicados
        if (!repo.findByDia(dia).isEmpty()) {
            return;
        }

        List<String> symbols = accountCryptoBalanceRepository.findDistinctSymbols();

        for (String raw : symbols) {
            if (raw == null) continue;
            String sym = raw.trim().toUpperCase();
            if (sym.isBlank()) continue;

            // ❌ Antes: if ("USDT".equals(sym) || "USDC".equals(sym)) continue;
            // ✅ Ahora: solo omitimos USDT
            if ("USDT".equals(sym)) continue;

            // Saldo total interno de esa cripto (todas las cuentas)
            Double saldoTotal = accountBinanceService.getTotalCryptoBalanceInterno(sym);
            if (saldoTotal == null || Math.abs(saldoTotal) < 1e-8) continue;

            CryptoAverageRate ultima = repo.findTopByCriptoOrderByFechaCalculoDesc(sym).orElse(null);

            Double tasaBase;
            if (ultima == null) {
                // Primera vez en la vida: usa precio de mercado
                tasaBase = getPrecioMercadoUsdt(sym);
            } else {
                // Días siguientes: parte de la tasa promedio anterior
                tasaBase = ultima.getTasaPromedioDia();
            }

            if (tasaBase == null || tasaBase <= 0) {
                continue;
            }

            CryptoAverageRate rate = new CryptoAverageRate();
            rate.setCripto(sym);
            rate.setDia(dia);
            rate.setFechaCalculo(fecha);

            rate.setSaldoInicialCripto(saldoTotal);
            rate.setTasaBaseUsdt(tasaBase);

            rate.setTotalCriptoCompradaDia(0.0);
            rate.setTotalUsdtComprasDia(0.0);

            rate.setTasaPromedioDia(tasaBase);
            rate.setSaldoFinalCripto(saldoTotal);

            repo.save(rate);
        }
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

        // Debe existir una tasa inicial para esa cripto
        CryptoAverageRate ultima = repo.findTopByCriptoOrderByFechaCalculoDesc(c)
                .orElseThrow(() -> new IllegalStateException(
                        "Primero debes configurar la tasa inicial para la cripto " + c));

        // 🔹 Saldo INTERNO actual total de esa cripto (todas las cuentas), después de aplicar la compra
        Double saldoTotalActualCripto = accountBinanceService.getTotalCryptoBalanceInterno(c);

        CryptoAverageRate snapshotDia = repo.findByCriptoAndDia(c, dia).orElse(null);

        if (snapshotDia == null) {
            // ===== Primera compra del día =====

            // Saldo inicial del día = saldo actual - compra actual
            Double saldoInicialDia = saldoTotalActualCripto - cantidadCriptoComprada;

            // Valuación del saldo inicial con la tasa promedio anterior
            Double usdtInicial = saldoInicialDia * ultima.getTasaPromedioDia();

            // Totales del día (saldo inicial + compras del día)
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
            // ===== Siguientes compras del mismo día =====

            Double saldoInicialDia = snapshotDia.getSaldoInicialCripto();
            Double tasaBase        = snapshotDia.getTasaBaseUsdt();

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

    
    @Override
    public List<CryptoPendienteDto> listarCriptosPendientesInicializacion() {
        List<String> symbols = accountCryptoBalanceRepository.findDistinctSymbols();
        List<CryptoPendienteDto> out = new ArrayList<>();

        for (String raw : symbols) {
            if (raw == null) continue;
            String sym = raw.trim().toUpperCase();
            if (sym.isBlank()) continue;

            // ❌ Antes: if ("USDT".equals(sym) || "USDC".equals(sym)) continue;
            // ✅ Ahora: solo omitimos USDT
            if ("USDT".equals(sym)) continue;

            // Si ya tiene tasa, se salta
            boolean yaTiene = repo.findTopByCriptoOrderByFechaCalculoDesc(sym).isPresent();
            if (yaTiene) continue;

            // 🔹 Saldo EXTERNO total de esa cripto
            Double saldoTotal = accountBinanceService.getTotalCryptoBalanceExterno(sym);
            if (saldoTotal == null || Math.abs(saldoTotal) < 1e-8) continue;

            out.add(new CryptoPendienteDto(sym, saldoTotal));
        }
        return out;
    }

    
    @Override
    public List<CryptoAverageRate> listarPorDia(LocalDate dia) {
        return repo.findByDia(dia);
    }
    
    private Double getPrecioMercadoUsdt(String cripto) {
        if (cripto == null) return 0.0;
        String sym = cripto.trim().toUpperCase();
        if (sym.isEmpty()) return 0.0;

        // Stables
        // ❌ Antes: if ("USDT".equals(sym) || "USDC".equals(sym)) { return 1.0; }
        // ✅ Ahora: solo USDT es fijo 1.0
        if ("USDT".equals(sym)) {
            return 1.0;
        }

        try {
            Double px = binanceService.getPriceInUSDT(sym);
            if (px != null && px > 0) {
                return px;
            }
        } catch (Exception e) {
            System.out.println("⚠️ No pude obtener precio de mercado para " + sym + ": " + e.getMessage());
        }
        return 0.0;
    }


    @Override
    public CryptoAverageRate inicializarCripto(String cripto, Double tasaInicialUsdt, LocalDateTime fecha) {
        String c = cripto.toUpperCase();

        // Solo si nunca se ha configurado esa cripto
        if (repo.findTopByCriptoOrderByFechaCalculoDesc(c).isPresent()) {
            throw new IllegalStateException("La cripto " + c + " ya tiene tasa inicial configurada.");
        }

        // 🔹 Saldo inicial REAL (interno totalmente sincronizado) de esa cripto
        // (usa la tabla account_crypto_balance, sumando todas las cuentas)
        Double saldoInicialCripto = accountBinanceService.getTotalCryptoBalanceInterno(c);

        LocalDate dia = fecha.atZone(ZONE_BOGOTA).toLocalDate();

        // 🔹 Si no me mandan tasa o es <= 0, tomo la del mercado
        Double tasaBase = tasaInicialUsdt;
        if (tasaBase == null || tasaBase <= 0) {
            tasaBase = getPrecioMercadoUsdt(c);
            if (tasaBase == null || tasaBase <= 0) {
                throw new IllegalStateException(
                    "No se pudo obtener precio de mercado para " + c + " al inicializar la tasa promedio."
                );
            }
        }

        CryptoAverageRate rate = new CryptoAverageRate();
        rate.setCripto(c);
        rate.setDia(dia);
        rate.setFechaCalculo(fecha);

        rate.setSaldoInicialCripto(saldoInicialCripto);
        rate.setTasaBaseUsdt(tasaBase);

        rate.setTotalCriptoCompradaDia(0.0);
        rate.setTotalUsdtComprasDia(0.0);

        rate.setTasaPromedioDia(tasaBase);
        rate.setSaldoFinalCripto(saldoInicialCripto);

        return repo.save(rate);
    }

    
    

}

