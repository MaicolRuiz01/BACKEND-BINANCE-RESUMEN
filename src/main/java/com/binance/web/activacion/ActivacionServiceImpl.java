package com.binance.web.activacion;

import com.binance.web.Entity.AccountCop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        return activacionSolicitudRepository.findFirstByConsumidaFalseOrderByCreadaEnAsc()
                .map(solicitud -> {
                    solicitud.setConsumida(true);
                    activacionSolicitudRepository.save(solicitud);
                    return solicitud.getCuenta();
                });
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
