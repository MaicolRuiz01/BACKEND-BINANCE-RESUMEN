package com.binance.web.serviceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import com.binance.web.Entity.VesAverageRate;
import com.binance.web.Repository.VesAverageRateRepository;
import com.binance.web.service.AccountVesService;
import com.binance.web.service.VesAverageRateService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VesAverageRateServiceImpl implements VesAverageRateService {
	
	private final VesAverageRateRepository repo;
    private final AccountVesService accountVesService;

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    @Override
    public VesAverageRate getUltima() {
        return repo.findTopByOrderByFechaCalculoDesc().orElse(null);
    }

    /**
     * Primera vez en la vida:
     *  - usuario pone tasaInicial manualmente
     *  - tomamos el inventario total de VES desde AccountVes
     */
    @Override
    @Transactional
    public VesAverageRate inicializarTasaInicial(Double tasaInicialCopPorVes, LocalDateTime fecha) {
        if (repo.count() > 0) {
            throw new IllegalStateException("La tasa promedio VES inicial ya fue configurada.");
        }
        if (tasaInicialCopPorVes == null || tasaInicialCopPorVes <= 0) {
            throw new IllegalArgumentException("La tasa inicial VES debe ser > 0.");
        }

        // Inventario total en VES (todas las cuentas VES)
        Double saldoInicialVes = accountVesService.getTotalSaldoVes();
        if (saldoInicialVes == null) saldoInicialVes = 0.0;

        LocalDate dia = fecha.atZone(ZONE_BOGOTA).toLocalDate();

        VesAverageRate rate = new VesAverageRate();
        rate.setDia(dia);
        rate.setFechaCalculo(fecha);

        rate.setSaldoInicialVes(saldoInicialVes);
        rate.setTasaBaseCop(tasaInicialCopPorVes);

        rate.setTotalVesCompradosDia(0.0);
        rate.setTotalPesosComprasDia(0.0);

        rate.setTasaPromedioDia(tasaInicialCopPorVes);
        rate.setSaldoFinalVes(saldoInicialVes);

        return repo.save(rate);
    }

    /**
     * Se llama cada vez que registras una CompraVES:
     * - cantidadVesComprada = compra.getBolivares()
     * - totalPesosCompra    = compra.getPesos()
     */
    @Override
    @Transactional
    public VesAverageRate actualizarPorCompra(
            Double cantidadVesComprada,
            Double totalPesosCompra,
            LocalDateTime fechaOperacion
    ) {
        if (cantidadVesComprada == null || cantidadVesComprada <= 0) return getUltima();
        if (totalPesosCompra == null || totalPesosCompra <= 0) return getUltima();

        LocalDate dia = fechaOperacion.atZone(ZONE_BOGOTA).toLocalDate();

        // 👇 AHORA ES OBLIGATORIO QUE YA EXISTA UNA TASA INICIAL
        VesAverageRate ultima = repo.findTopByOrderByFechaCalculoDesc()
                .orElseThrow(() -> new IllegalStateException(
                        "Primero debes configurar la tasa promedio inicial VES."
                ));

        Double saldoActualVes = accountVesService.getTotalSaldoVes();
        if (saldoActualVes == null) saldoActualVes = 0.0;

        VesAverageRate snapshotDia = repo.findByDia(dia).stream().findFirst().orElse(null);

        if (snapshotDia == null) {
            // Primera compra del día (ya con tasa base existente)
            Double saldoInicialDia = saldoActualVes - cantidadVesComprada;
            Double tasaBase        = ultima.getTasaPromedioDia();

            Double pesosSaldoInicial = saldoInicialDia * tasaBase;
            Double totalVesDia       = saldoInicialDia + cantidadVesComprada;
            Double totalPesosDia     = pesosSaldoInicial + totalPesosCompra;

            Double nuevaTasa = totalVesDia > 0 ? totalPesosDia / totalVesDia : tasaBase;

            snapshotDia = new VesAverageRate();
            snapshotDia.setDia(dia);
            snapshotDia.setFechaCalculo(fechaOperacion);

            snapshotDia.setSaldoInicialVes(saldoInicialDia);
            snapshotDia.setTasaBaseCop(tasaBase);
            snapshotDia.setTotalVesCompradosDia(cantidadVesComprada);
            snapshotDia.setTotalPesosComprasDia(totalPesosCompra);
            snapshotDia.setTasaPromedioDia(nuevaTasa);
            snapshotDia.setSaldoFinalVes(saldoActualVes);

        } else {
            // Más compras del mismo día
            Double saldoInicialDia = snapshotDia.getSaldoInicialVes();
            Double tasaBase        = snapshotDia.getTasaBaseCop();

            Double pesosSaldoInicial    = saldoInicialDia * tasaBase;
            Double totalVesCompradosDia = snapshotDia.getTotalVesCompradosDia() + cantidadVesComprada;
            Double totalPesosComprasDia = snapshotDia.getTotalPesosComprasDia() + totalPesosCompra;

            Double totalVesDia   = saldoInicialDia + totalVesCompradosDia;
            Double totalPesosDia = pesosSaldoInicial + totalPesosComprasDia;

            Double nuevaTasa = totalVesDia > 0 ? totalPesosDia / totalVesDia : tasaBase;

            snapshotDia.setFechaCalculo(fechaOperacion);
            snapshotDia.setTotalVesCompradosDia(totalVesCompradosDia);
            snapshotDia.setTotalPesosComprasDia(totalPesosComprasDia);
            snapshotDia.setTasaPromedioDia(nuevaTasa);
            snapshotDia.setSaldoFinalVes(saldoActualVes);
        }

        return repo.save(snapshotDia);
    }


    @Override
    public List<VesAverageRate> listarPorDia(LocalDate dia) {
        return repo.findByDia(dia);
    }

}
