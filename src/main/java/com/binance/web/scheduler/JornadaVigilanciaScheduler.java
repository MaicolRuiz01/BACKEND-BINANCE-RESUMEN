package com.binance.web.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.binance.web.BinanceAPI.P2PActiveOrderService;
import com.binance.web.Entity.JornadaTrabajo;
import com.binance.web.Entity.ModoJornada;
import com.binance.web.Repository.JornadaTrabajoRepository;
import com.binance.web.service.TelegramService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Vigila las jornadas EN CURSO y avisa por Telegram según el modo en que trabaja el operador.
 *
 *  VENTA_USDT → si pasan {@code venta.sin-ordenes-min} minutos sin NINGUNA venta P2P en curso,
 *               manda una alerta y la repite cada ese mismo intervalo mientras siga en seco.
 *               Se calla solo apenas entra una orden. Vigila TODA la jornada, no solo al inicio,
 *               para cachar anuncio caído / cuenta restringida a media jornada.
 *
 *  CAJA       → no es alarma: cada {@code caja.aviso-min} minutos manda un aviso de estado
 *               ("X lleva N h haciendo caja"), porque cuadrar caja puede demorarse.
 *
 * Diseño defensivo: si Telegram o la BD fallan, se loguea y se sigue — nunca tumba la app.
 * El estado (última venta vista / última alerta) se persiste en la jornada, así que un
 * reinicio de Railway no hace que se pierda ni que se dispare una ráfaga de avisos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JornadaVigilanciaScheduler {

    private final JornadaTrabajoRepository jornadaRepository;
    private final P2PActiveOrderService activeOrderService;
    private final TelegramService telegramService;

    @Value("${app.telegram.group-chat-id:}")
    private String grupoChatId;

    /** Minutos en seco (sin ventas en curso) antes de alertar, y cada cuánto se repite. */
    @Value("${jornada.venta.sin-ordenes-min:5}")
    private long minutosSinOrdenes;

    /** Cada cuántos minutos se manda el aviso de estado de caja. */
    @Value("${jornada.caja.aviso-min:60}")
    private long minutosAvisoCaja;

    /**
     * Corre cada minuto: es suficiente resolución para un umbral de 5 min y es muy barato
     * (lee jornadas abiertas de BD y un booleano en memoria, sin llamar a Binance).
     */
    @Scheduled(fixedDelayString = "${jornada.vigilancia.interval-ms:60000}", initialDelay = 60000)
    public void vigilar() {
        if (grupoChatId == null || grupoChatId.isBlank()) return; // Telegram no configurado

        List<JornadaTrabajo> abiertas;
        try {
            abiertas = jornadaRepository.findByEndedAtIsNull();
        } catch (Exception e) {
            log.warn("[Jornada] No se pudieron leer las jornadas en curso: {}", e.getMessage());
            return;
        }
        if (abiertas.isEmpty()) return;

        LocalDateTime ahora = LocalDateTime.now();
        boolean hayVentas = activeOrderService.hayOrdenesActivas();
        // Si aún no se hizo el primer poll, no sabemos si hay órdenes → no alarmamos en falso.
        boolean datosConfiables = activeOrderService.yaHizoPrimerPoll();

        for (JornadaTrabajo j : abiertas) {
            try {
                if (j.getModo() == ModoJornada.VENTA_USDT) {
                    vigilarVenta(j, ahora, hayVentas, datosConfiables);
                } else if (j.getModo() == ModoJornada.CAJA) {
                    vigilarCaja(j, ahora);
                }
                // modo null (jornadas viejas) → no se vigila
            } catch (Exception e) {
                log.warn("[Jornada] Error vigilando la jornada de {}: {}", j.getUsername(), e.getMessage());
            }
        }
    }

    /** VENTA_USDT: alerta si lleva demasiado tiempo sin ventas en curso. */
    private void vigilarVenta(JornadaTrabajo j, LocalDateTime ahora, boolean hayVentas, boolean datosConfiables) {
        if (hayVentas) {
            // Hay órdenes: se actualiza la referencia y se limpia el estado de alerta,
            // para que si vuelve a secarse el flujo se cuente de cero otra vez.
            j.setUltimaVentaVistaAt(ahora);
            j.setUltimaAlertaAt(null);
            jornadaRepository.save(j);
            return;
        }

        if (!datosConfiables) return;

        // Referencia: la última vez que vimos ventas; si nunca, desde que arrancó la jornada.
        LocalDateTime referencia = j.getUltimaVentaVistaAt() != null ? j.getUltimaVentaVistaAt() : j.getStartedAt();
        if (referencia == null) return;

        long minutosEnSeco = Duration.between(referencia, ahora).toMinutes();
        if (minutosEnSeco < minutosSinOrdenes) return;

        // Repetir solo cada N minutos, no en cada tick del scheduler.
        if (j.getUltimaAlertaAt() != null
                && Duration.between(j.getUltimaAlertaAt(), ahora).toMinutes() < minutosSinOrdenes) {
            return;
        }

        String msg = String.format(
                "⚠️ *Sin ventas P2P*%n%nOperador: *%s*%nLleva *%d min* sin órdenes en curso.%n%n"
                        + "Revisa que el anuncio esté publicado y que la cuenta no esté restringida.",
                j.getUsername(), minutosEnSeco);
        telegramService.sendMessage(grupoChatId, msg);

        j.setUltimaAlertaAt(ahora);
        jornadaRepository.save(j);
        log.info("[Jornada] Alerta de sin-ventas enviada para {} ({} min en seco)", j.getUsername(), minutosEnSeco);
    }

    /** CAJA: aviso periódico de cuánto lleva el operador cuadrando caja. */
    private void vigilarCaja(JornadaTrabajo j, LocalDateTime ahora) {
        LocalDateTime base = j.getUltimaAlertaAt() != null ? j.getUltimaAlertaAt() : j.getStartedAt();
        if (base == null) return;

        if (Duration.between(base, ahora).toMinutes() < minutosAvisoCaja) return;

        long minutosTotales = Duration.between(j.getStartedAt(), ahora).toMinutes();
        String tiempo = minutosTotales >= 60
                ? String.format("%d h %02d min", minutosTotales / 60, minutosTotales % 60)
                : String.format("%d min", minutosTotales);

        String msg = String.format("🧾 El usuario *%s* lleva *%s* haciendo caja.", j.getUsername(), tiempo);
        telegramService.sendMessage(grupoChatId, msg);

        j.setUltimaAlertaAt(ahora);
        jornadaRepository.save(j);
        log.info("[Jornada] Aviso de caja enviado para {} ({})", j.getUsername(), tiempo);
    }
}
