package com.binance.web.activacion;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Repository.AccountCopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Cola de activaciones de monitoreo — ver ActivacionSolicitud para el
 * contexto completo. A propósito minimalista: esta clase SOLO gestiona la
 * cola (encolar / consumir / registrar el ack inmediato del bot). Nunca
 * toca el estado de la cuenta (bloqueada, disponibleBanco, etc.) — eso lo
 * hace, de forma segura, ConciliacionBancariaService.registrarResultadoCuenta,
 * disparado únicamente por los eventos "conexion_exitosa"/"error_login" de
 * /movimientos/evento (ver MovimientosBridgeServiceImpl), que sí son prueba
 * real de una conexión intentada de verdad — nunca por esto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivacionServiceImpl implements ActivacionService {

    private final ActivacionSolicitudRepository activacionSolicitudRepository;
    private final AccountCopRepository accountCopRepository;

    @Override
    @Transactional
    public void solicitarActivacion(AccountCop cuenta) {
        if (cuenta == null || cuenta.getName() == null || cuenta.getName().isBlank()) return;

        // Dedupe: si ya hay una solicitud pendiente para esta cuenta, no crear otra.
        if (activacionSolicitudRepository.findFirstByCuentaAndConsumidaFalse(cuenta.getName()).isPresent()) {
            log.info("[Activación] Ya había una solicitud pendiente para '{}' — no se duplica.", cuenta.getName());
            return;
        }

        ActivacionSolicitud solicitud = new ActivacionSolicitud();
        solicitud.setCuenta(cuenta.getName());
        solicitud.setCreadaEn(LocalDateTime.now());
        solicitud.setConsumida(false);
        activacionSolicitudRepository.save(solicitud);
        log.info("[Activación] Encolada solicitud de activación para '{}'.", cuenta.getName());
    }

    @Override
    @Transactional
    public Optional<String> obtenerYConsumirPendiente() {
        // Incidente 31/08/2026: como esta cola nunca expiraba ni se cancelaba
        // entre sí (ver DetencionServiceImpl), una cuenta que se activaba y
        // desactivaba varias veces en P2P mientras el bot estaba apagado
        // (p.ej. por la rotación automática de JornadaController) dejaba
        // filas encoladas de AMBAS colas, sin relación con su estado ACTUAL.
        // Al arrancar el bot, se le entregaba todo el historial de golpe —
        // incluidas activaciones de cuentas que ya no estaban seleccionadas
        // en P2P (saturando RAM de Chrome con logins innecesarios). Ahora,
        // antes de entregar una solicitud, se revisa el estado REAL de la
        // cuenta en AccountCop; si ya no aplica, se descarta (se marca
        // consumida igual, para no reprocesarla) y se sigue con la siguiente.
        Optional<ActivacionSolicitud> siguiente;
        while ((siguiente = activacionSolicitudRepository.findFirstByConsumidaFalseOrderByCreadaEnAsc()).isPresent()) {
            ActivacionSolicitud solicitud = siguiente.get();
            solicitud.setConsumida(true);
            activacionSolicitudRepository.save(solicitud);

            String cuenta = solicitud.getCuenta();
            // findAllByName (no findByName): hay nombres duplicados en la tabla
            // y findByName lanza IncorrectResultSizeDataAccessException apenas
            // encuentra más de una (bug real encontrado el 31/08/2026, tumbaba
            // /movimientos/detencion/pendiente con 400 en cada poll). Si el
            // nombre es ambiguo (0 o >1 resultados), se trata como "no activa"
            // — más seguro descartar la solicitud que reventar el endpoint.
            List<AccountCop> coincidencias = accountCopRepository.findAllByName(cuenta);
            boolean activaEnP2PAhora = coincidencias.size() == 1
                    && Boolean.TRUE.equals(coincidencias.get(0).getActivaParaP2P());

            if (activaEnP2PAhora) {
                return Optional.of(cuenta);
            }
            log.info("[Activación] Descartada solicitud obsoleta de '{}' — ya no está activa en P2P "
                    + "(se encoló en su momento, pero el estado cambió antes de que el bot la consumiera).", cuenta);
        }
        return Optional.empty();
    }

    @Override
    public void procesarResultado(ActivacionResultadoDto resultado) {
        if (resultado == null || resultado.getCuenta() == null) {
            log.warn("[Activación] Resultado recibido sin cuenta — ignorado.");
            return;
        }
        if (Boolean.TRUE.equals(resultado.getIniciada())) {
            log.info("[Activación] '{}' — monitoreo iniciado (o ya estaba activo).", resultado.getCuenta());
        } else {
            // "No se pudo arrancar el hilo" (típicamente sin credenciales en
            // Bitwarden en la máquina del bot) — NO es un bloqueo del banco,
            // así que esto solo se loguea para revisión manual, nunca toca
            // el estado de la cuenta.
            log.warn("[Activación] '{}' — no se pudo iniciar monitoreo: {}",
                    resultado.getCuenta(), resultado.getError());
        }
    }
}
