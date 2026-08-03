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
import com.binance.web.auth.JornadaSseController;
import com.binance.web.service.AnuncioVigilanciaService;
import com.binance.web.service.TelegramService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Vigila las jornadas EN CURSO y aplica el control de operadores en ventas P2P.
 *
 * ── Reglas del modo VENTA_USDT ────────────────────────────────────────────────
 *  1) Sin anuncio publicado dentro de los primeros {@code jornada.anuncio.gracia-min} minutos
 *     desde que arrancó la jornada → se PAUSA el cronómetro.
 *  2) Con anuncio publicado pero sin ninguna venta en curso durante
 *     {@code jornada.venta.sin-ordenes-min} minutos → se le avisa al operador que le baje un
 *     punto a la tasa, y al administrador por Telegram (incluyendo la tasa actual del anuncio).
 *  3) Si pasados {@code jornada.tasa.margen-min} minutos desde ese aviso el anuncio sigue con
 *     la misma tasa (no bajó al menos {@code jornada.tasa.punto-cop}) → se PAUSA el cronómetro
 *     con el mensaje "tu tiempo de trabajo se detuvo" y se reporta por Telegram.
 *  4) Cualquier cambio de tasa (subió o bajó) en los anuncios propios se reporta por Telegram.
 *     Esta regla es global, no depende de que haya jornadas abiertas.
 *
 * ── Modo CAJA ─────────────────────────────────────────────────────────────────
 *  Sin cambios: aviso de estado cada {@code jornada.caja.aviso-min} minutos.
 *
 * ── Criterios de diseño ───────────────────────────────────────────────────────
 *  · PAUSAR ≠ CERRAR: la jornada sigue abierta, solo deja de contar el tiempo (y de pagarse).
 *    El operador la reanuda desde la app cuando corrige lo que la disparó.
 *  · Nunca se castiga con datos dudosos: si Binance no respondió, no se pausa a nadie. Un
 *    problema de red no puede costarle el sueldo a un operador que sí tenía el anuncio arriba.
 *  · Una jornada ya pausada se ignora por completo: no se re-pausa ni se le repiten avisos.
 *  · Todo el estado vive en la BD, así que un reinicio de Railway no dispara una ráfaga de
 *    mensajes ni pierde las cuentas regresivas en curso.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JornadaVigilanciaScheduler {

    private final JornadaTrabajoRepository jornadaRepository;
    private final P2PActiveOrderService activeOrderService;
    private final AnuncioVigilanciaService anuncioVigilancia;
    private final TelegramService telegramService;

    @Value("${app.telegram.group-chat-id:}")
    private String grupoChatId;

    /** Minutos de gracia desde que arranca la jornada para tener el anuncio publicado. */
    @Value("${jornada.anuncio.gracia-min:10}")
    private long graciaAnuncioMin;

    /** Minutos sin ventas en curso (con anuncio publicado) antes de mandar el aviso de tasa. */
    @Value("${jornada.venta.sin-ordenes-min:10}")
    private long minutosSinOrdenes;

    /** Minutos que se le dan al operador para bajar la tasa antes de pausarle el cronómetro. */
    @Value("${jornada.tasa.margen-min:3}")
    private long margenBajarTasaMin;

    /** Cuánto es "un punto" de tasa, en pesos. */
    @Value("${jornada.tasa.punto-cop:1}")
    private double puntoCop;

    /** Cada cuántos minutos se manda el aviso de estado de caja. */
    @Value("${jornada.caja.aviso-min:60}")
    private long minutosAvisoCaja;

    /**
     * Corre cada minuto. Con las ventanas de 10 y 3 minutos, un minuto de resolución es
     * suficiente y mantiene acotadas las consultas a Binance (la foto de anuncios va cacheada).
     */
    @Scheduled(fixedDelayString = "${jornada.vigilancia.interval-ms:60000}", initialDelay = 60000)
    public void vigilar() {
        // Regla 4: es independiente de las jornadas, así que corre siempre.
        try {
            anuncioVigilancia.detectarYReportarCambiosDeTasa();
        } catch (Exception e) {
            log.warn("[Jornada] Error revisando cambios de tasa: {}", e.getMessage());
        }

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
        boolean ordenesConfiables = activeOrderService.yaHizoPrimerPoll();

        for (JornadaTrabajo j : abiertas) {
            try {
                // Una jornada pausada no se vigila: ya está detenida, esperando que la reanuden.
                if (j.getPausadaAt() != null) continue;

                if (j.getModo() == ModoJornada.VENTA_USDT) {
                    vigilarVenta(j, ahora, hayVentas, ordenesConfiables);
                } else if (j.getModo() == ModoJornada.CAJA) {
                    vigilarCaja(j, ahora);
                }
                // modo null (jornadas viejas) → no se vigila
            } catch (Exception e) {
                log.warn("[Jornada] Error vigilando la jornada de {}: {}", j.getUsername(), e.getMessage());
            }
        }
    }

    // ── VENTA_USDT ────────────────────────────────────────────────

    private void vigilarVenta(JornadaTrabajo j, LocalDateTime ahora, boolean hayVentas, boolean ordenesConfiables) {

        boolean hayAnuncio        = anuncioVigilancia.hayAnuncioPublicado();
        boolean anunciosConfiables = anuncioVigilancia.datosConfiables();

        // Registrar la primera vez que se le ve anuncio (corta la regla 1 para siempre).
        if (hayAnuncio && j.getTuvoAnuncioAt() == null) {
            j.setTuvoAnuncioAt(ahora);
            jornadaRepository.save(j);
        }

        // ── REGLA 1: nunca publicó anuncio dentro del margen de gracia ──
        if (!hayAnuncio && j.getTuvoAnuncioAt() == null) {
            if (!anunciosConfiables) return; // Binance no respondió: no se castiga a ciegas
            if (j.getStartedAt() == null) return;

            long minutosDesdeInicio = Duration.between(j.getStartedAt(), ahora).toMinutes();
            if (minutosDesdeInicio >= graciaAnuncioMin) {
                pausar(j, ahora,
                        String.format("No hay ningún anuncio publicado en Binance después de %d minutos "
                                + "desde que iniciaste la jornada.", minutosDesdeInicio),
                        String.format("⏸️ *Jornada detenida — sin anuncio*%n%nOperador: *%s*%n"
                                + "Pasaron *%d min* desde que inició y no hay ningún anuncio publicado "
                                + "en las cuentas registradas.", j.getUsername(), minutosDesdeInicio));
            }
            return; // sin anuncio no aplican las demás reglas
        }

        // Si no hay anuncio pero SÍ lo tuvo antes, no se pausa por la regla 1 (ya la superó).
        if (!hayAnuncio) return;

        // ── Hay ventas: todo en orden, se reinician los contadores ──
        if (hayVentas) {
            j.setUltimaVentaVistaAt(ahora);
            j.setUltimaAlertaAt(null);
            j.setAvisoTasaAt(null);
            j.setTasaAlAvisar(null);
            j.setAdvNoAvisado(null);
            jornadaRepository.save(j);
            return;
        }

        if (!ordenesConfiables) return;

        // ── REGLA 3: ya se le avisó, ¿bajó la tasa dentro del margen? ──
        if (j.getAvisoTasaAt() != null) {
            long minutosDesdeAviso = Duration.between(j.getAvisoTasaAt(), ahora).toMinutes();
            if (minutosDesdeAviso < margenBajarTasaMin) return; // todavía está en tiempo

            if (!anunciosConfiables) return; // no se pausa con datos dudosos

            Double tasaActual = anuncioVigilancia.tasaDe(j.getAdvNoAvisado());
            if (tasaActual == null) tasaActual = anuncioVigilancia.tasaReferencia();
            Double tasaPrevia = j.getTasaAlAvisar();

            if (tasaActual == null || tasaPrevia == null) return; // sin datos para comparar

            double bajada = tasaPrevia - tasaActual;
            if (bajada >= puntoCop) {
                // Obedeció: se limpia el aviso y se le vuelve a dar margen desde ahora.
                j.setAvisoTasaAt(null);
                j.setTasaAlAvisar(null);
                j.setAdvNoAvisado(null);
                j.setUltimaVentaVistaAt(ahora); // se le reinicia la cuenta de "en seco"
                jornadaRepository.save(j);
                telegramService.sendMessage(grupoChatId, String.format(
                        "✅ *Tasa corregida*%n%nOperador: *%s*%nBajó la tasa de %s a %s.",
                        j.getUsername(), fmt(tasaPrevia), fmt(tasaActual)));
                log.info("[Jornada] {} bajó la tasa {} → {}", j.getUsername(), tasaPrevia, tasaActual);
                return;
            }

            // No la bajó → se detiene el cronómetro.
            pausar(j, ahora,
                    "Tu tiempo de trabajo se detuvo: pasaron " + minutosDesdeAviso
                            + " minutos y el anuncio sigue con la misma tasa (" + fmt(tasaActual) + ").",
                    String.format("⏸️ *Jornada detenida — no bajó la tasa*%n%nOperador: *%s*%n"
                            + "Se le avisó hace *%d min* y el anuncio sigue en *%s*.%n"
                            + "Tasa al momento del aviso: %s.",
                            j.getUsername(), minutosDesdeAviso, fmt(tasaActual), fmt(tasaPrevia)));
            return;
        }

        // ── REGLA 2: hay anuncio pero no entran ventas ──
        LocalDateTime referencia = j.getUltimaVentaVistaAt() != null ? j.getUltimaVentaVistaAt() : j.getStartedAt();
        if (referencia == null) return;

        long minutosEnSeco = Duration.between(referencia, ahora).toMinutes();
        if (minutosEnSeco < minutosSinOrdenes) return;

        Double tasa = anuncioVigilancia.tasaReferencia();
        String advNo = anuncioVigilancia.advNoReferencia();

        String avisoOperador = tasa != null
                ? String.format("Llevas %d minutos sin ventas. Bájale un punto a la tasa del anuncio "
                        + "(está en %s). Tienes %d minutos para hacerlo.", minutosEnSeco, fmt(tasa), margenBajarTasaMin)
                : String.format("Llevas %d minutos sin ventas. Bájale un punto a la tasa del anuncio. "
                        + "Tienes %d minutos para hacerlo.", minutosEnSeco, margenBajarTasaMin);

        String avisoAdmin = String.format(
                "⚠️ *Sin ventas P2P*%n%nOperador: *%s*%nLleva *%d min* sin órdenes en curso.%n"
                        + "Tasa actual del anuncio: *%s*%n%nSe le pidió bajar un punto; tiene %d min.",
                j.getUsername(), minutosEnSeco, tasa != null ? fmt(tasa) : "no disponible", margenBajarTasaMin);

        j.setAvisoTasaAt(ahora);
        j.setTasaAlAvisar(tasa);
        j.setAdvNoAvisado(advNo);
        j.setUltimaAlertaAt(ahora);
        j.setAvisoPendiente(avisoOperador);
        j.setAvisoPendienteAt(ahora);
        jornadaRepository.save(j);

        avisarOperador(j.getUsername(), avisoOperador);
        enviarTelegram(avisoAdmin);
        log.info("[Jornada] Aviso de tasa enviado a {} ({} min en seco, tasa {})",
                j.getUsername(), minutosEnSeco, tasa);
    }

    // ── CAJA (sin cambios respecto al comportamiento anterior) ────

    private void vigilarCaja(JornadaTrabajo j, LocalDateTime ahora) {
        LocalDateTime base = j.getUltimaAlertaAt() != null ? j.getUltimaAlertaAt() : j.getStartedAt();
        if (base == null) return;

        if (Duration.between(base, ahora).toMinutes() < minutosAvisoCaja) return;

        long minutosTotales = Duration.between(j.getStartedAt(), ahora).toMinutes();
        String tiempo = minutosTotales >= 60
                ? String.format("%d h %02d min", minutosTotales / 60, minutosTotales % 60)
                : String.format("%d min", minutosTotales);

        String msg = String.format("🧾 El usuario *%s* lleva *%s* haciendo caja.", j.getUsername(), tiempo);
        enviarTelegram(msg);

        j.setUltimaAlertaAt(ahora);
        jornadaRepository.save(j);
        log.info("[Jornada] Aviso de caja enviado para {} ({})", j.getUsername(), tiempo);
    }

    // ── Acciones ──────────────────────────────────────────────────

    /**
     * Detiene el cronómetro sin cerrar la jornada. El tiempo en pausa no se paga; el operador
     * la reanuda desde la app cuando corrige el problema.
     */
    private void pausar(JornadaTrabajo j, LocalDateTime ahora, String motivoOperador, String msgTelegram) {
        j.setPausadaAt(ahora);
        j.setMotivoPausa(motivoOperador);
        j.setAvisoPendiente(motivoOperador);
        j.setAvisoPendienteAt(ahora);
        // Se limpia la cuenta regresiva de tasa: al reanudar arranca de cero.
        j.setAvisoTasaAt(null);
        j.setTasaAlAvisar(null);
        j.setAdvNoAvisado(null);
        jornadaRepository.save(j);

        try {
            if (JornadaSseController.INSTANCE != null) {
                JornadaSseController.INSTANCE.notificarPausa(j.getUsername(), motivoOperador);
            }
        } catch (Exception e) {
            log.warn("[Jornada] No se pudo notificar la pausa por SSE: {}", e.getMessage());
        }

        enviarTelegram(msgTelegram);
        log.info("[Jornada] Jornada de {} PAUSADA: {}", j.getUsername(), motivoOperador);
    }

    private void avisarOperador(String username, String mensaje) {
        try {
            if (JornadaSseController.INSTANCE != null) {
                JornadaSseController.INSTANCE.notificarAviso(username, mensaje);
            }
        } catch (Exception e) {
            log.warn("[Jornada] No se pudo enviar el aviso por SSE: {}", e.getMessage());
        }
    }

    private void enviarTelegram(String msg) {
        try {
            if (grupoChatId != null && !grupoChatId.isBlank()) {
                telegramService.sendMessage(grupoChatId, msg);
            }
        } catch (Exception e) {
            log.warn("[Jornada] No se pudo enviar el mensaje de Telegram: {}", e.getMessage());
        }
    }

    private String fmt(double v) {
        return String.format("%,.0f", v);
    }
}
