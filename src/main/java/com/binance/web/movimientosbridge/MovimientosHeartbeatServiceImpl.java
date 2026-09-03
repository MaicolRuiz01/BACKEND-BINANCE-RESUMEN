package com.binance.web.movimientosbridge;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.activacion.ActivacionService;
import com.binance.web.detencion.DetencionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reconciliación periódica activación/detención — ver MovimientosHeartbeatService.
 *
 * Por qué existe (03/09/2026): tanto la activación como la detención de una
 * cuenta son "avisa una vez y olvídate" (ver ActivacionServiceImpl /
 * DetencionServiceImpl) — si el aviso se pierde (p.ej. justo cuando Chrome
 * se reinicia por RAM alta en Movimientos, ver sesiones.py), nadie vuelve a
 * intentarlo y la discrepancia queda ahí para siempre. Caso real que motivó
 * esto: "Yeiner Rodriguez Ortega" se quedó corriendo en Movimientos varios
 * minutos después de deseleccionarse en P2P porque la detención nunca llegó
 * a aplicarse.
 *
 * Pochonance (este backend, en Railway) no puede "mirar" la terminal de
 * Movimientos porque corre en una máquina distinta a la de Milton — así que
 * en vez de eso, Movimientos le REPORTA su propio estado cada cierto tiempo
 * (ver pochonance_activador.py → _loop_heartbeat, mismo mecanismo de
 * polling que ya usa para /conciliacion/pendiente), y acá se compara contra
 * lo que la base de datos dice que debería estar pasando. El costo es una
 * comparación de dos listas de ~20-30 nombres — nada caro, no hace falta
 * revisar cuenta por cuenta ni guardar historial.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovimientosHeartbeatServiceImpl implements MovimientosHeartbeatService {

    private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}");

    private final AccountCopRepository accountCopRepository;
    private final ActivacionService activacionService;
    private final DetencionService detencionService;

    /** Mismo criterio de normalización que ConciliacionBancariaServiceImpl —
     *  minúsculas, sin tildes, espacios colapsados, para que un espacio de
     *  más o un acento distinto no rompa la comparación (ver el caso real
     *  "Ana Peñaranda " con espacio final, visto en los logs de Movimientos). */
    private static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = DIACRITICOS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return sinTildes.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    @Override
    public void reconciliar(List<String> cuentasActivasReportadas) {
        try {
            List<String> reportadas = cuentasActivasReportadas != null ? cuentasActivasReportadas : List.of();

            Set<String> reportadasNormalizadas = new HashSet<>();
            for (String nombre : reportadas) {
                reportadasNormalizadas.add(normalizar(nombre));
            }

            // Una sola consulta con TODAS las cuentas — se usa tanto para el
            // chequeo de activación (activaParaP2P=true) como para resolver
            // cada nombre reportado, sin ir dos veces a la base de datos.
            List<AccountCop> todas = accountCopRepository.findAll();
            Map<String, List<AccountCop>> porNombreNormalizado = new HashMap<>();
            for (AccountCop c : todas) {
                porNombreNormalizado.computeIfAbsent(normalizar(c.getName()), k -> new ArrayList<>()).add(c);
            }

            // 1) Activas en P2P que Movimientos NO reporta corriendo → falta activarlas.
            for (AccountCop cuenta : todas) {
                if (!Boolean.TRUE.equals(cuenta.getActivaParaP2P())) continue;
                if (!reportadasNormalizadas.contains(normalizar(cuenta.getName()))) {
                    log.warn("[Heartbeat] '{}' está activa en P2P pero Movimientos no la reporta corriendo — "
                            + "reencolando activación.", cuenta.getName());
                    activacionService.solicitarActivacion(cuenta);
                }
            }

            // 2) Corriendo en Movimientos pero ya NO activas en P2P (o desconocidas/ambiguas) → detenerlas.
            for (String nombreReportado : reportadas) {
                List<AccountCop> coincidencias = porNombreNormalizado.get(normalizar(nombreReportado));
                if (coincidencias == null || coincidencias.size() != 1) {
                    log.warn("[Heartbeat] '{}' reportada corriendo por Movimientos pero no se encontró (o es "
                            + "ambigua) en AccountCop — se ignora por seguridad.", nombreReportado);
                    continue;
                }
                AccountCop cuenta = coincidencias.get(0);
                if (!Boolean.TRUE.equals(cuenta.getActivaParaP2P())) {
                    log.warn("[Heartbeat] '{}' sigue corriendo en Movimientos pero ya no está activa en P2P — "
                            + "reencolando detención.", cuenta.getName());
                    detencionService.solicitarDetencion(cuenta);
                }
            }
        } catch (Exception e) {
            // Best-effort a propósito: un heartbeat fallido no debe tumbar el
            // ciclo de polling del bot ni el endpoint — se reintenta solo en
            // el próximo ciclo (2 minutos después).
            log.error("[Heartbeat] Error reconciliando estado — se ignora, se reintenta en el próximo ciclo.", e);
        }
    }
}
