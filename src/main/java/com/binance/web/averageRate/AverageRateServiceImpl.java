package com.binance.web.averageRate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Repository.AverageRateRepository;

@Service
public class AverageRateServiceImpl implements AverageRateService{
	
	
	@Autowired private AverageRateRepository averageRateRepository;
	
	@Autowired private AccountBinanceService accountBinanceService;
	
	private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

	@Override
	public AverageRate getUltimaTasaPromedio() {
		return averageRateRepository.findTopByOrderByIdDesc().orElse(null);
	}

	@Override
	public AverageRate guardarNuevaTasa(Double nuevaTasa, Double nuevoSaldo, LocalDateTime fecha) {
		
		AverageRate tasaPromedio = new AverageRate();
		tasaPromedio.setAverageRate(nuevaTasa);
		tasaPromedio.setSaldoTotalInterno(nuevoSaldo);
		tasaPromedio.setFecha(fecha);
		return averageRateRepository.save(tasaPromedio);
	}
	
	@Override
    public AverageRate inicializarTasaPromedioInicial(Double tasaInicial, LocalDateTime fecha) {
        if (averageRateRepository.count() > 0) {
            throw new IllegalStateException("La tasa promedio inicial ya fue configurada.");
        }

        Double saldoInicialUsdt = accountBinanceService.getTotalExternalBalance().doubleValue();

        // Día lógico (pero solo como variable local)
        LocalDate dia = fecha.atZone(ZONE_BOGOTA).toLocalDate();
        LocalDateTime inicioDia = dia.atStartOfDay(); // LocalDateTime

        AverageRate rate = new AverageRate();
        rate.setFecha(fecha);
        rate.setInicioDia(inicioDia);
        rate.setSaldoInicialDia(saldoInicialUsdt);
        rate.setTasaBaseSaldoInicial(tasaInicial);
        rate.setTotalUsdtComprasDia(0.0);
        rate.setTotalPesosComprasDia(0.0);
        rate.setAverageRate(tasaInicial);
        rate.setSaldoTotalInterno(saldoInicialUsdt);

        return averageRateRepository.save(rate);
    }

    @Override
    public AverageRate actualizarTasaPromedioPorCompra(
            LocalDateTime fechaCompra,
            Double montoUsdtCompra,
            Double tasaCompra
    ) {
        // Día lógico a partir de la fecha de la compra (solo local, no en DB)
        LocalDate dia = fechaCompra.atZone(ZONE_BOGOTA).toLocalDate();
        LocalDateTime inicioDia = dia.atStartOfDay(); // este sí se guarda

        AverageRate ultima = averageRateRepository
                .findTopByOrderByFechaDesc()
                .orElseThrow(() -> new IllegalStateException(
                        "Debe existir una tasa promedio inicial antes de asignar compras."
                ));

        Double saldoTotalInternoActual = accountBinanceService
                .getTotalExternalBalance()
                .doubleValue();

        AverageRate snapshotDia = averageRateRepository.findByInicioDia(inicioDia).orElse(null);

        if (snapshotDia == null) {
            // ===== Primera compra del día =====
            Double saldoInicialDia = saldoTotalInternoActual - montoUsdtCompra;
            Double tasaBaseSaldoInicial = ultima.getAverageRate();
            Double pesosSaldoInicial = saldoInicialDia * tasaBaseSaldoInicial;

            Double pesosCompraActual = montoUsdtCompra * tasaCompra;

            Double totalUsdtComprasDia = montoUsdtCompra;
            Double totalPesosComprasDia = pesosCompraActual;

            Double totalUsdtDia = saldoInicialDia + totalUsdtComprasDia;
            Double totalPesosDia = pesosSaldoInicial + totalPesosComprasDia;

            Double nuevaTasaPromedio = totalPesosDia / totalUsdtDia;

            snapshotDia = new AverageRate();
            snapshotDia.setFecha(fechaCompra);
            snapshotDia.setInicioDia(inicioDia);
            snapshotDia.setSaldoInicialDia(saldoInicialDia);
            snapshotDia.setTasaBaseSaldoInicial(tasaBaseSaldoInicial);
            snapshotDia.setTotalUsdtComprasDia(totalUsdtComprasDia);
            snapshotDia.setTotalPesosComprasDia(totalPesosComprasDia);
            snapshotDia.setAverageRate(nuevaTasaPromedio);
            snapshotDia.setSaldoTotalInterno(saldoTotalInternoActual);

        } else {
            // ===== Siguientes compras del mismo día =====
            Double saldoInicialDia = snapshotDia.getSaldoInicialDia();
            Double tasaBaseSaldoInicial = snapshotDia.getTasaBaseSaldoInicial();
            Double pesosSaldoInicial = saldoInicialDia * tasaBaseSaldoInicial;

            Double totalUsdtComprasDia = snapshotDia.getTotalUsdtComprasDia() + montoUsdtCompra;
            Double totalPesosComprasDia = snapshotDia.getTotalPesosComprasDia()
                    + (montoUsdtCompra * tasaCompra);

            Double totalUsdtDia = saldoInicialDia + totalUsdtComprasDia;
            Double totalPesosDia = pesosSaldoInicial + totalPesosComprasDia;

            Double nuevaTasaPromedio = totalPesosDia / totalUsdtDia;

            snapshotDia.setFecha(fechaCompra);
            snapshotDia.setTotalUsdtComprasDia(totalUsdtComprasDia);
            snapshotDia.setTotalPesosComprasDia(totalPesosComprasDia);
            snapshotDia.setAverageRate(nuevaTasaPromedio);
            snapshotDia.setSaldoTotalInterno(saldoTotalInternoActual);
        }

        return averageRateRepository.save(snapshotDia);
    }

    @Override
    public AverageRate getTasaPorDia(LocalDateTime fecha) {
        LocalDate dia = fecha.atZone(ZONE_BOGOTA).toLocalDate();
        LocalDateTime inicioDia = dia.atStartOfDay();
        return averageRateRepository.findByInicioDia(inicioDia).orElse(null);
    }


}
