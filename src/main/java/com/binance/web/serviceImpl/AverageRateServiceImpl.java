package com.binance.web.serviceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.AverageRate;
import com.binance.web.Repository.AverageRateRepository;
import com.binance.web.service.AccountBinanceService;
import com.binance.web.service.AverageRateService;

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
        rate.setSesionAbierta(false); // la tasa inicial es la base, no una sesión abierta

        return averageRateRepository.save(rate);
    }

    @Override
    public AverageRate actualizarTasaPromedioPorCompra(
            LocalDateTime fechaCompra,
            Double montoUsdtCompra,
            Double tasaCompra,
            boolean esUltimaSinAsignar
    ) {
        // NUEVA LÓGICA — corte por SESIÓN, no por día calendario:
        //  - Mientras haya compras (dollars) sin asignar, la SESIÓN sigue abierta y cada compra
        //    que se asigna se licúa contra la misma base (no se hace corte diario).
        //  - La base de una sesión NUEVA es la última tasa promedio vigente (el promedio real
        //    del inventario), tomada al abrir la sesión.
        //  - Cuando se asigna la ÚLTIMA compra pendiente (esUltimaSinAsignar), la sesión se cierra
        //    y su tasa queda como base de la próxima sesión.
        // Esto evita el bug de asignar compras atrasadas: ya no se recalcula ningún día pasado.

        LocalDateTime ahora = LocalDateTime.now(ZONE_BOGOTA);
        LocalDate dia = ahora.atZone(ZONE_BOGOTA).toLocalDate();
        LocalDateTime inicioDia = dia.atStartOfDay();

        Double saldoTotalInternoActual = accountBinanceService
                .getTotalExternalBalance()
                .doubleValue();

        AverageRate sesion = averageRateRepository.findTopBySesionAbiertaTrueOrderByFechaDesc().orElse(null);

        if (sesion == null) {
            // ===== No hay sesión abierta → se ABRE una nueva (primera compra del backlog) =====
            AverageRate ultima = averageRateRepository
                    .findTopByOrderByFechaDesc()
                    .orElseThrow(() -> new IllegalStateException(
                            "Debe existir una tasa promedio inicial antes de asignar compras."));

            // Inventario ANTES de esta compra. Se acota a >= 0: en este negocio el USDT fluye
            // (entra y sale rápido), así que el saldo externo actual puede ser MENOR que la compra
            // que se está asignando. Si no se acotara, saldoInicial quedaría NEGATIVO y el promedio
            // ponderado se dispararía FUERA del rango [tasaCompra, tasaBase] (bug: subía en vez de
            // bajar al asignar una compra más barata).
            Double saldoInicial = Math.max(0.0, saldoTotalInternoActual - montoUsdtCompra);
            Double tasaBase = ultima.getAverageRate();                       // base = última tasa vigente
            Double pesosSaldoInicial = saldoInicial * tasaBase;

            Double totalUsdtCompras = montoUsdtCompra;
            Double totalPesosCompras = montoUsdtCompra * tasaCompra;

            Double totalUsdt = saldoInicial + totalUsdtCompras;
            Double totalPesos = pesosSaldoInicial + totalPesosCompras;
            Double nuevaTasa = totalUsdt != 0 ? (totalPesos / totalUsdt) : tasaBase;

            sesion = new AverageRate();
            sesion.setInicioDia(inicioDia);
            sesion.setSaldoInicialDia(saldoInicial);
            sesion.setTasaBaseSaldoInicial(tasaBase);
            sesion.setTotalUsdtComprasDia(totalUsdtCompras);
            sesion.setTotalPesosComprasDia(totalPesosCompras);
            sesion.setAverageRate(nuevaTasa);
            sesion.setSaldoTotalInterno(saldoTotalInternoActual);
        } else {
            // ===== Sesión ya abierta → se licúa la compra contra la MISMA base =====
            // Acotar a >= 0 por si una sesión anterior guardó saldoInicial negativo (bug viejo).
            Double saldoInicial = Math.max(0.0, sesion.getSaldoInicialDia() != null ? sesion.getSaldoInicialDia() : 0.0);
            Double tasaBase = sesion.getTasaBaseSaldoInicial();
            Double pesosSaldoInicial = saldoInicial * tasaBase;

            Double totalUsdtCompras = sesion.getTotalUsdtComprasDia() + montoUsdtCompra;
            Double totalPesosCompras = sesion.getTotalPesosComprasDia() + (montoUsdtCompra * tasaCompra);

            Double totalUsdt = saldoInicial + totalUsdtCompras;
            Double totalPesos = pesosSaldoInicial + totalPesosCompras;
            Double nuevaTasa = totalUsdt != 0 ? (totalPesos / totalUsdt) : sesion.getAverageRate();

            sesion.setTotalUsdtComprasDia(totalUsdtCompras);
            sesion.setTotalPesosComprasDia(totalPesosCompras);
            sesion.setAverageRate(nuevaTasa);
            sesion.setSaldoTotalInterno(saldoTotalInternoActual);
        }

        sesion.setFecha(ahora);
        // La sesión queda ABIERTA mientras queden compras sin asignar; se CIERRA con la última.
        sesion.setSesionAbierta(!esUltimaSinAsignar);

        return averageRateRepository.save(sesion);
    }

    @Override
    public AverageRate getTasaPorDia(LocalDateTime fecha) {
        LocalDate dia = fecha.atZone(ZONE_BOGOTA).toLocalDate();
        LocalDateTime inicioDia = dia.atStartOfDay();
        return averageRateRepository.findByInicioDia(inicioDia).orElse(null);
    }


}
