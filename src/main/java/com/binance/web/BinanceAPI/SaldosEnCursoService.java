package com.binance.web.BinanceAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.P2PPreAsignacionRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Saldos de la vista "Ventas en curso", calculados en el BACKEND y en UNA sola lectura.
 *
 * Por cuenta COP devuelve:
 *   - balance: saldo real guardado                                      → verde.
 *   - enCurso: ventas en curso pre-asignadas que aún no se importaron   → amarillo = balance + enCurso.
 *
 * POR QUÉ ACÁ Y NO EN LA PANTALLA: antes la pantalla sumaba el saldo (de la BD) con las órdenes
 * (de Binance), que llegan por caminos y en momentos distintos. Cuando una venta se completaba, en
 * un momento la pantalla la tenía en las dos partes (contada doble) o en ninguna (desaparecía un
 * rato): los saldos "se ponían locos".
 *
 * Acá el monto en curso sale de p2p_pre_asignacion, y esa fila se borra en la MISMA transacción en
 * la que la venta importada suma al saldo real. Leyendo ambas cosas dentro de una transacción
 * (misma foto de la BD) el monto está en un lado o en el otro, nunca en los dos ni en ninguno.
 *
 * FILTRO DE HUÉRFANAS: solo suman las filas de órdenes que están activas en Binance o que acaban de
 * salir del listado esperando su importación ({@link P2PActiveOrderService#ordenesQueCuentanEnCurso}).
 * Sin este filtro, una pre-asignación de una orden cancelada/vencida que nunca se importó se quedaba
 * sumando en el amarillo de la cuenta (hasta la limpieza de 48 h) aunque no hubiera ninguna venta
 * en curso a la vista.
 */
@Slf4j
@Service
public class SaldosEnCursoService {

    @Autowired private AccountCopRepository accountCopRepository;
    @Autowired private P2PPreAsignacionRepository preAsignacionRepository;
    @Autowired private P2PActiveOrderService activeOrderService;

    /** Huérfanas ya reportadas en el log (para no repetir el aviso en cada refresco). */
    private final Set<String> huerfanasReportadas = ConcurrentHashMap.newKeySet();

    public record SaldoEnCurso(Integer id, Double balance,
                               Double cupoCajeroDisponibleHoy, Double cupoCorresponsalDisponibleHoy,
                               double enCurso, List<DetalleEnCurso> detalle) {}

    /** Cada venta que compone el monto en curso — para poder ver de dónde sale el amarillo. */
    public record DetalleEnCurso(String orderNumber, double pesos) {}

    @Transactional(readOnly = true)
    public List<SaldoEnCurso> calcular() {
        // Antes del primer poll (recién arrancado el backend) aún no se sabe qué está activo:
        // en ese caso se cuentan todas, como antes, en vez de mostrar el amarillo vacío.
        Set<String> vigentes = activeOrderService.yaHizoPrimerPoll()
                ? activeOrderService.ordenesQueCuentanEnCurso()
                : null;

        Map<Integer, Double> enCurso = new HashMap<>();
        Map<Integer, List<DetalleEnCurso>> detalle = new HashMap<>();
        for (P2PPreAsignacionRepository.PreSinImportar p : preAsignacionRepository.findSinImportar()) {
            if (p.getCopId() == null) continue;
            if (vigentes != null && !vigentes.contains(p.getOrderNumber())) {
                if (huerfanasReportadas.add(p.getOrderNumber())) {
                    log.warn("[SaldosEnCurso] Pre-asignación HUÉRFANA ignorada en el amarillo: orden {} → {} ({} miles), creada {}. "
                            + "No está activa en Binance ni se importó como venta.",
                            p.getOrderNumber(), p.getCopNombre(), p.getPesosCop(), p.getCreatedAt());
                }
                continue;
            }
            double pesos = p.getPesosCop() != null ? p.getPesosCop() : 0.0;
            enCurso.merge(p.getCopId(), pesos, Double::sum);
            detalle.computeIfAbsent(p.getCopId(), k -> new ArrayList<>())
                    .add(new DetalleEnCurso(p.getOrderNumber(), pesos));
        }

        List<SaldoEnCurso> out = new ArrayList<>();
        for (AccountCopRepository.SaldoView a : accountCopRepository.findAllSaldos()) {
            out.add(new SaldoEnCurso(a.getId(), a.getBalance(),
                    a.getCupoCajeroDisponibleHoy(), a.getCupoCorresponsalDisponibleHoy(),
                    enCurso.getOrDefault(a.getId(), 0.0),
                    detalle.getOrDefault(a.getId(), List.of())));
        }
        return out;
    }
}
