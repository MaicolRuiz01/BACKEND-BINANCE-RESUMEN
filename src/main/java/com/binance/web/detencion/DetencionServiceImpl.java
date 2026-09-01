package com.binance.web.detencion;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Repository.AccountCopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Cola de detenciones de monitoreo — ver DetencionSolicitud para el
 * contexto completo. Igual que ActivacionServiceImpl, a propósito
 * minimalista: solo gestiona la cola. Quien decide CUÁNDO una cuenta debe
 * dejar de monitorearse es CuentaP2PSyncService (reacciona a que
 * activaParaP2P haya pasado a false), nunca esta clase.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DetencionServiceImpl implements DetencionService {

    private final DetencionSolicitudRepository detencionSolicitudRepository;
    private final AccountCopRepository accountCopRepository;

    @Override
    @Transactional
    public void solicitarDetencion(AccountCop cuenta) {
        if (cuenta == null || cuenta.getName() == null || cuenta.getName().isBlank()) return;

        if (detencionSolicitudRepository.findFirstByCuentaAndConsumidaFalse(cuenta.getName()).isPresent()) {
            log.info("[Detención] Ya había una solicitud pendiente para '{}' — no se duplica.", cuenta.getName());
            return;
        }

        DetencionSolicitud solicitud = new DetencionSolicitud();
        solicitud.setCuenta(cuenta.getName());
        solicitud.setCreadaEn(LocalDateTime.now());
        solicitud.setConsumida(false);
        detencionSolicitudRepository.save(solicitud);
        log.info("[Detención] Encolada solicitud de detención para '{}'.", cuenta.getName());
    }

    @Override
    @Transactional
    public Optional<String> obtenerYConsumirPendiente() {
        // Mismo fix que ActivacionServiceImpl (incidente 31/08/2026): antes de
        // entregar una solicitud de detención se revisa el estado REAL de la
        // cuenta — si volvió a estar activa en P2P antes de que el bot la
        // consumiera, la solicitud ya no aplica y se descarta.
        Optional<DetencionSolicitud> siguiente;
        while ((siguiente = detencionSolicitudRepository.findFirstByConsumidaFalseOrderByCreadaEnAsc()).isPresent()) {
            DetencionSolicitud solicitud = siguiente.get();
            solicitud.setConsumida(true);
            detencionSolicitudRepository.save(solicitud);

            String cuenta = solicitud.getCuenta();
            AccountCop cuentaActual = accountCopRepository.findByName(cuenta);
            boolean activaEnP2PAhora = cuentaActual != null && Boolean.TRUE.equals(cuentaActual.getActivaParaP2P());

            if (!activaEnP2PAhora) {
                return Optional.of(cuenta);
            }
            log.info("[Detención] Descartada solicitud obsoleta de '{}' — volvió a estar activa en P2P "
                    + "antes de que el bot la consumiera.", cuenta);
        }
        return Optional.empty();
    }

    @Override
    public void procesarResultado(DetencionResultadoDto resultado) {
        if (resultado == null || resultado.getCuenta() == null) {
            log.warn("[Detención] Resultado recibido sin cuenta — ignorado.");
            return;
        }
        if (Boolean.TRUE.equals(resultado.getDetenida())) {
            log.info("[Detención] '{}' — monitoreo detenido (o ya no estaba activo).", resultado.getCuenta());
        } else {
            log.warn("[Detención] '{}' — no se pudo detener el monitoreo: {}",
                    resultado.getCuenta(), resultado.getError());
        }
    }
}
