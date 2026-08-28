package com.binance.web.serviceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.TasaPromedioDiagnostico;
import com.binance.web.Repository.AverageRateRepository;
import com.binance.web.service.AccountBinanceService;
import com.binance.web.service.AverageRateService;

@Service
public class AverageRateServiceImpl implements AverageRateService{
	
	
	@Autowired private AverageRateRepository averageRateRepository;

	@Autowired private AccountBinanceService accountBinanceService;

	/** Solo para el registro de diagnóstico; no interviene en ningún cálculo. */
	@Autowired private com.binance.web.Repository.TasaPromedioDiagnosticoRepository diagnosticoRepository;

	@Autowired private com.binance.web.Repository.BuyDollarsRepository buyDollarsRepository;
	
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
            Integer buyDollarsId,
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

        // ── Diagnóstico: valores intermedios que después no se pueden recuperar ──
        // Se recogen mientras se calcula y se guardan al final en tasa_promedio_diagnostico.
        // No influyen en el resultado: son solo para poder revisar qué pasó.
        String diagEvento = (sesion == null) ? "APERTURA_SESION" : "LICUA_SESION";
        Double diagTasaAnterior = null;
        Double diagOtrosPendientes = null;
        Double diagSaldoBase = null;
        Double diagTasaBase = null;
        Double diagUsdtAcum = null;
        Double diagPesosAcum = null;
        Double diagTotalUsdt = null;
        Double diagTotalPesos = null;
        boolean diagBaseRecortada = false;

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
            //
            // También se resta el USDT de OTRAS compras aún sin asignar: ese USDT ya está en el
            // wallet (por eso saldoTotalInternoActual lo incluye) pero todavía no tiene tasa real.
            // Si se dejara dentro de saldoInicial quedaría valorado a la tasaBase (vieja) y luego,
            // cuando le llegue su turno de asignación, se volvería a sumar con su tasa real →
            // quedaría contado DOS veces en el promedio ponderado.
            Double otrosPendientes = buyDollarsRepository.sumAmountPendienteExcluyendo(buyDollarsId);
            if (otrosPendientes == null) otrosPendientes = 0.0;
            Double saldoSinRecortar = saldoTotalInternoActual - montoUsdtCompra - otrosPendientes;
            Double saldoInicial = Math.max(0.0, saldoSinRecortar);
            Double tasaBase = ultima.getAverageRate();                       // base = última tasa vigente
            Double pesosSaldoInicial = saldoInicial * tasaBase;

            Double totalUsdtCompras = montoUsdtCompra;
            Double totalPesosCompras = montoUsdtCompra * tasaCompra;

            Double totalUsdt = saldoInicial + totalUsdtCompras;
            Double totalPesos = pesosSaldoInicial + totalPesosCompras;
            Double nuevaTasa = totalUsdt != 0 ? (totalPesos / totalUsdt) : tasaBase;

            diagTasaAnterior = ultima.getAverageRate();
            diagOtrosPendientes = otrosPendientes;
            diagSaldoBase = saldoInicial;
            diagTasaBase = tasaBase;
            diagUsdtAcum = totalUsdtCompras;
            diagPesosAcum = totalPesosCompras;
            diagTotalUsdt = totalUsdt;
            diagTotalPesos = totalPesos;
            // Si el saldo era menor que la compra, la base se pierde y el promedio pasa a ser
            // simplemente la tasa de esta compra. Vale la pena poder detectar cuándo ocurre.
            diagBaseRecortada = saldoSinRecortar < 0;

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

            diagTasaAnterior = sesion.getAverageRate();
            diagSaldoBase = saldoInicial;
            diagTasaBase = tasaBase;
            diagUsdtAcum = totalUsdtCompras;
            diagPesosAcum = totalPesosCompras;
            diagTotalUsdt = totalUsdt;
            diagTotalPesos = totalPesos;

            sesion.setTotalUsdtComprasDia(totalUsdtCompras);
            sesion.setTotalPesosComprasDia(totalPesosCompras);
            sesion.setAverageRate(nuevaTasa);
            sesion.setSaldoTotalInterno(saldoTotalInternoActual);
        }

        sesion.setFecha(ahora);
        // La sesión queda ABIERTA mientras queden compras sin asignar; se CIERRA con la última.
        sesion.setSesionAbierta(!esUltimaSinAsignar);

        AverageRate guardada = averageRateRepository.save(sesion);

        // ── Registro de diagnóstico ──
        // Va en try/catch y al final a propósito: si falla, la asignación de la compra ya se
        // hizo y no puede quedar a medias por un problema al escribir un dato informativo.
        try {
            TasaPromedioDiagnostico d = new TasaPromedioDiagnostico();
            d.setFecha(ahora);
            d.setEvento(diagEvento);
            d.setBuyDollarsId(buyDollarsId);
            d.setCompraUsdt(montoUsdtCompra);
            d.setCompraTasa(tasaCompra);
            d.setCompraPesos(montoUsdtCompra != null && tasaCompra != null
                    ? montoUsdtCompra * tasaCompra : null);
            d.setSaldoExternoLeido(saldoTotalInternoActual);
            d.setOtrosPendientesUsdt(diagOtrosPendientes);
            d.setSaldoBaseUsdt(diagSaldoBase);
            d.setTasaBase(diagTasaBase);
            d.setPesosBase(diagSaldoBase != null && diagTasaBase != null
                    ? diagSaldoBase * diagTasaBase : null);
            d.setBaseRecortadaACero(diagBaseRecortada);
            d.setUsdtAcumSesion(diagUsdtAcum);
            d.setPesosAcumSesion(diagPesosAcum);
            d.setTasaAnterior(diagTasaAnterior);
            d.setTasaResultante(guardada.getAverageRate());
            d.setTotalUsdt(diagTotalUsdt);
            d.setTotalPesos(diagTotalPesos);
            d.setAverageRateId(guardada.getId());
            d.setSesionAbierta(guardada.getSesionAbierta());
            d.setInicioDia(guardada.getInicioDia());
            diagnosticoRepository.save(d);
        } catch (Exception e) {
            System.out.println("[TasaPromedio][DIAG] No se pudo registrar el diagnóstico: " + e.getMessage());
        }

        return guardada;
    }

    @Override
    public AverageRate getTasaPorDia(LocalDateTime fecha) {
        LocalDate dia = fecha.atZone(ZONE_BOGOTA).toLocalDate();
        LocalDateTime inicioDia = dia.atStartOfDay();
        return averageRateRepository.findByInicioDia(inicioDia).orElse(null);
    }


}
