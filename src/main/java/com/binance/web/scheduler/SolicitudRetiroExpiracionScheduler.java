package com.binance.web.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.binance.web.Entity.EstadoSolicitud;
import com.binance.web.Entity.SolicitudRetiro;
import com.binance.web.Repository.SolicitudRetiroRepository;
import com.binance.web.service.RetiradorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cancela automáticamente las solicitudes de retiro que llevan más de N
 * minutos sin confirmarse (por defecto 240 = 4 horas) — ya sea SIN_ASIGNAR
 * (publicada en el grupo, nadie la tomó) o PENDIENTE (un retirador la tomó
 * pero nunca presionó "Ya hice el retiro").
 *
 * No hay saldo, caja ni cupo diario que revertir: el dinero solo se descuenta
 * cuando la solicitud se CONFIRMA, así que cancelar antes de eso no toca
 * ningún movimiento real. Reusa exactamente la misma lógica que el botón
 * "Cancelar" manual (RetiradorService.cancelarSolicitud): borra los mensajes
 * de Telegram (grupo y privado) para que quede "como si nunca se hubiera
 * enviado", y deja la solicitud en estado CANCELADO en la base de datos para
 * que siga apareciendo en el historial del retirador.
 *
 * El umbral (solicitud.expiracion.minutos) y el intervalo de revisión
 * (solicitud.expiracion.interval-ms) son configurables por si se necesita
 * probar con valores cortos sin tocar este código — ver application.properties.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SolicitudRetiroExpiracionScheduler {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    @Value("${solicitud.expiracion.minutos:240}")
    private long minutosExpiracion;

    private final SolicitudRetiroRepository solicitudRepository;
    private final RetiradorService retiradorService;

    /** Cada 15 minutos por defecto (y poco después de arrancar): cancela solicitudes vencidas. */
    @Scheduled(fixedDelayString = "${solicitud.expiracion.interval-ms:900000}", initialDelay = 60000)
    public void cancelarSolicitudesExpiradas() {
        LocalDateTime limite = LocalDateTime.now(ZONE).minusMinutes(minutosExpiracion);
        List<SolicitudRetiro> expiradas = solicitudRepository.findByEstadoInAndFechaCreacionBefore(
                List.of(EstadoSolicitud.SIN_ASIGNAR, EstadoSolicitud.PENDIENTE), limite);

        if (expiradas.isEmpty()) {
            return;
        }

        int canceladas = 0;
        for (SolicitudRetiro s : expiradas) {
            try {
                retiradorService.cancelarSolicitud(s.getId());
                canceladas++;
                log.info("[Expiración] Solicitud #{} cancelada automáticamente: llevaba más de {} min sin confirmarse.",
                        s.getId(), minutosExpiracion);
            } catch (Exception e) {
                // No frenamos el resto del lote por una que falle (ej: justo se confirmó
                // en el instante entre la consulta y el intento de cancelar).
                log.error("[Expiración] No se pudo cancelar automáticamente la solicitud #{}", s.getId(), e);
            }
        }

        log.info("[Expiración] {} solicitud(es) de retiro canceladas automáticamente por pasar {} min sin confirmar.",
                canceladas, minutosExpiracion);
    }
}
