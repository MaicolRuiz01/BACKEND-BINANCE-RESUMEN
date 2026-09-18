package com.binance.web.BinanceAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.P2PPreAsignacionRepository;

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
 */
@Service
public class SaldosEnCursoService {

    @Autowired private AccountCopRepository accountCopRepository;
    @Autowired private P2PPreAsignacionRepository preAsignacionRepository;

    public record SaldoEnCurso(Integer id, Double balance,
                               Double cupoCajeroDisponibleHoy, Double cupoCorresponsalDisponibleHoy,
                               double enCurso) {}

    @Transactional(readOnly = true)
    public List<SaldoEnCurso> calcular() {
        Map<Integer, Double> enCurso = new HashMap<>();
        for (P2PPreAsignacionRepository.SumaEnCurso s : preAsignacionRepository.sumarEnCursoPorCuenta()) {
            if (s.getCopId() == null) continue;
            enCurso.merge(s.getCopId(), s.getTotal() != null ? s.getTotal() : 0.0, Double::sum);
        }

        List<SaldoEnCurso> out = new ArrayList<>();
        for (AccountCopRepository.SaldoView a : accountCopRepository.findAllSaldos()) {
            out.add(new SaldoEnCurso(a.getId(), a.getBalance(),
                    a.getCupoCajeroDisponibleHoy(), a.getCupoCorresponsalDisponibleHoy(),
                    enCurso.getOrDefault(a.getId(), 0.0)));
        }
        return out;
    }
}
