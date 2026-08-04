package com.binance.web.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.JornadaTrabajo;
import com.binance.web.Entity.ModoJornada;
import com.binance.web.Entity.Usuario;
import com.binance.web.Repository.JornadaTrabajoRepository;
import com.binance.web.service.TelegramService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Jornada de trabajo del operador: el botón "Empecé a trabajar" / "Terminé".
 * Mide el tiempo por el que efectivamente se le paga (distinto de la sesión con la app abierta).
 * El usuario se toma del token (principal autenticado), no del body.
 */
@Slf4j
@RestController
@RequestMapping("/auth/jornada")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class JornadaController {

    private final JornadaTrabajoRepository jornadaRepository;
    private final TelegramService telegramService;

    @Value("${app.telegram.group-chat-id:}")
    private String grupoChatId;

    /**
     * Avisar también los inicios y finales de jornada. Por defecto NO, porque con varios
     * operadores entrando y saliendo el grupo se llena de mensajes y termina siendo ruido que
     * nadie lee. Las REANUDACIONES sí se avisan siempre: son el evento que de otra forma queda
     * invisible (el operador se levanta su propia pausa y nadie se entera).
     */
    @Value("${jornada.telegram.avisar-inicio-fin:false}")
    private boolean avisarInicioFin;

    /**
     * Inicia una jornada. Si ya hay una en curso, la devuelve (idempotente).
     * Body opcional: { "modo": "VENTA_USDT" | "CAJA" } — define qué vigilancia se le aplica.
     * Si no se manda modo, la jornada queda sin vigilancia (comportamiento anterior).
     */
    @PostMapping("/iniciar")
    public ResponseEntity<Map<String, Object>> iniciar(@AuthenticationPrincipal Usuario user,
                                                      @RequestBody(required = false) Map<String, String> body) {
        if (user == null) return ResponseEntity.status(401).build();

        JornadaTrabajo jornada = jornadaRepository
                .findFirstByUsernameAndEndedAtIsNullOrderByStartedAtDesc(user.getUsername())
                .orElse(null);

        if (jornada == null) {
            jornada = new JornadaTrabajo();
            jornada.setUsername(user.getUsername());
            jornada.setRol(user.getRol());
            jornada.setStartedAt(LocalDateTime.now());
            jornada.setModo(parseModo(body));
            jornada = jornadaRepository.save(jornada);

            if (avisarInicioFin) {
                enviarTelegram(String.format("🟢 *Inicio de jornada*%n%nOperador: *%s*%nModo: %s",
                        user.getUsername(),
                        jornada.getModo() != null ? etiquetaModo(jornada.getModo()) : "sin definir"));
            }
        } else if (jornada.getModo() == null) {
            // Jornada ya abierta sin modo (o de antes de esta función): se le asigna el elegido.
            ModoJornada modo = parseModo(body);
            if (modo != null) {
                jornada.setModo(modo);
                jornada = jornadaRepository.save(jornada);
            }
        }
        return ResponseEntity.ok(toMap(jornada));
    }

    /** Lee el modo del body de forma tolerante: si viene vacío o inválido, devuelve null. */
    private ModoJornada parseModo(Map<String, String> body) {
        if (body == null) return null;
        String raw = body.get("modo");
        if (raw == null || raw.isBlank()) return null;
        try {
            return ModoJornada.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Reanuda una jornada que la vigilancia pausó automáticamente.
     * Acumula el tiempo que estuvo detenida para que NO se le pague, y limpia el aviso.
     */
    @PostMapping("/reanudar")
    public ResponseEntity<Map<String, Object>> reanudar(@AuthenticationPrincipal Usuario user) {
        if (user == null) return ResponseEntity.status(401).build();

        JornadaTrabajo jornada = jornadaRepository
                .findFirstByUsernameAndEndedAtIsNullOrderByStartedAtDesc(user.getUsername())
                .orElse(null);

        if (jornada == null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("activa", false);
            return ResponseEntity.ok(m);
        }

        if (jornada.getPausadaAt() != null) {
            // Se guardan antes de limpiarlos, para poder contarle al admin qué pasó.
            String motivoOriginal = jornada.getMotivoPausa();
            long segundosDetenido = Math.max(0,
                    Duration.between(jornada.getPausadaAt(), LocalDateTime.now()).getSeconds());

            long pausados = jornada.getSegundosPausados() != null ? jornada.getSegundosPausados() : 0L;
            pausados += segundosDetenido;
            jornada.setSegundosPausados(pausados);
            jornada.setPausadaAt(null);
            jornada.setMotivoPausa(null);
            // Al reanudar se le da margen limpio: la cuenta de "en seco" arranca desde ahora.
            jornada.setUltimaVentaVistaAt(LocalDateTime.now());
            jornada.setUltimaAlertaAt(null);
            jornada.setAvisoPendiente(null);
            jornada.setAvisoPendienteAt(null);
            jornada = jornadaRepository.save(jornada);

            try {
                if (JornadaSseController.INSTANCE != null) {
                    JornadaSseController.INSTANCE.notificarReanudada(user.getUsername());
                }
            } catch (Exception ignored) { /* el SSE es el canal rápido, no el confiable */ }

            // El operador se levanta su propia pausa, así que este aviso es el único registro
            // que ve el administrador de que volvió a contar el tiempo, y de cuánto estuvo parado.
            enviarTelegram(String.format(
                    "▶️ *Jornada reanudada*%n%nOperador: *%s*%nEstuvo detenido *%s* (no se le paga).%n%nMotivo: %s",
                    user.getUsername(), formatDuracion(segundosDetenido),
                    motivoOriginal != null ? motivoOriginal : "no registrado"));
        }
        return ResponseEntity.ok(toMap(jornada));
    }

    /**
     * El operador ya vio el aviso: se limpia para que no se le repita al recargar.
     * No afecta la pausa (esa solo se levanta reanudando).
     */
    @PostMapping("/aviso-visto")
    public ResponseEntity<Map<String, Object>> avisoVisto(@AuthenticationPrincipal Usuario user) {
        if (user == null) return ResponseEntity.status(401).build();

        JornadaTrabajo jornada = jornadaRepository
                .findFirstByUsernameAndEndedAtIsNullOrderByStartedAtDesc(user.getUsername())
                .orElse(null);

        if (jornada != null && jornada.getAvisoPendiente() != null && jornada.getPausadaAt() == null) {
            jornada.setAvisoPendiente(null);
            jornada.setAvisoPendienteAt(null);
            jornada = jornadaRepository.save(jornada);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        return ResponseEntity.ok(m);
    }

    /** Termina la jornada en curso (si hay). */
    @PostMapping("/finalizar")
    public ResponseEntity<Map<String, Object>> finalizar(@AuthenticationPrincipal Usuario user) {
        if (user == null) return ResponseEntity.status(401).build();

        JornadaTrabajo jornada = jornadaRepository
                .findFirstByUsernameAndEndedAtIsNullOrderByStartedAtDesc(user.getUsername())
                .orElse(null);

        if (jornada != null) {
            LocalDateTime ahora = LocalDateTime.now();
            // Si se termina estando pausada, se cierra primero la pausa para que ese tiempo
            // quede contabilizado como no pagado y no se pierda al cerrar la jornada.
            if (jornada.getPausadaAt() != null) {
                long pausados = jornada.getSegundosPausados() != null ? jornada.getSegundosPausados() : 0L;
                pausados += Math.max(0, Duration.between(jornada.getPausadaAt(), ahora).getSeconds());
                jornada.setSegundosPausados(pausados);
                jornada.setPausadaAt(null);
            }
            jornada.setEndedAt(ahora);
            jornada.setAvisoPendiente(null);
            jornada.setAvisoPendienteAt(null);
            jornada = jornadaRepository.save(jornada);

            if (avisarInicioFin) {
                // Se informa el tiempo PAGABLE (ya sin las pausas), que es el dato que importa.
                long seg = 0;
                if (jornada.getStartedAt() != null) {
                    seg = Math.max(0, Duration.between(jornada.getStartedAt(), ahora).getSeconds());
                    long pausados = jornada.getSegundosPausados() != null ? jornada.getSegundosPausados() : 0L;
                    seg = Math.max(0, seg - pausados);
                }
                enviarTelegram(String.format("🔴 *Fin de jornada*%n%nOperador: *%s*%nTiempo pagable: *%s*",
                        user.getUsername(), formatDuracion(seg)));
            }
            return ResponseEntity.ok(toMap(jornada));
        }
        // No había jornada abierta: responde estado "sin jornada".
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activa", false);
        return ResponseEntity.ok(m);
    }

    /** Estado actual: si el usuario tiene una jornada en curso (para restaurar el botón al recargar). */
    @GetMapping("/actual")
    public ResponseEntity<Map<String, Object>> actual(@AuthenticationPrincipal Usuario user) {
        if (user == null) return ResponseEntity.status(401).build();

        JornadaTrabajo jornada = jornadaRepository
                .findFirstByUsernameAndEndedAtIsNullOrderByStartedAtDesc(user.getUsername())
                .orElse(null);

        if (jornada == null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("activa", false);
            return ResponseEntity.ok(m);
        }
        return ResponseEntity.ok(toMap(jornada));
    }

    /**
     * Manda un mensaje al grupo sin que un fallo de Telegram tumbe la operación del operador.
     * Si Telegram está caído, el peor caso es que el admin no reciba el aviso — pero la jornada
     * se reanuda o se cierra igual, que es lo que le importa a quien está trabajando.
     */
    private void enviarTelegram(String msg) {
        if (grupoChatId == null || grupoChatId.isBlank()) return;
        try {
            telegramService.sendMessage(grupoChatId, msg);
        } catch (Exception e) {
            log.warn("[Jornada] No se pudo enviar el aviso por Telegram: {}", e.getMessage());
        }
    }

    private String formatDuracion(long segundos) {
        long h = segundos / 3600;
        long m = (segundos % 3600) / 60;
        if (h > 0) return String.format("%d h %02d min", h, m);
        if (m > 0) return String.format("%d min", m);
        return String.format("%d s", segundos);
    }

    private String etiquetaModo(ModoJornada modo) {
        return modo == ModoJornada.VENTA_USDT ? "Vendiendo USDT" : "Haciendo caja";
    }

    private Map<String, Object> toMap(JornadaTrabajo j) {
        boolean activa = j.getEndedAt() == null;
        boolean pausada = activa && j.getPausadaAt() != null;
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fin = activa ? ahora : j.getEndedAt();

        long seg = 0;
        if (j.getStartedAt() != null) {
            seg = Math.max(0, Duration.between(j.getStartedAt(), fin).getSeconds());

            // El tiempo en pausa NO se paga: se descuenta lo ya acumulado y, si está pausada
            // ahora mismo, también lo que lleva detenida en este momento.
            long pausados = j.getSegundosPausados() != null ? j.getSegundosPausados() : 0L;
            if (pausada) {
                pausados += Math.max(0, Duration.between(j.getPausadaAt(), ahora).getSeconds());
            }
            seg = Math.max(0, seg - pausados);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("activa", activa);
        m.put("modo", j.getModo() != null ? j.getModo().name() : null);
        m.put("startedAt", j.getStartedAt() != null ? j.getStartedAt().toString() : null);
        m.put("endedAt", j.getEndedAt() != null ? j.getEndedAt().toString() : null);
        m.put("transcurridoSegundos", seg);
        // Estado de la vigilancia automática (lo usa el topbar para congelar el cronómetro,
        // mostrar el motivo y ofrecer el botón de reanudar).
        m.put("pausada", pausada);
        m.put("motivoPausa", j.getMotivoPausa());
        m.put("pausadaAt", j.getPausadaAt() != null ? j.getPausadaAt().toString() : null);
        m.put("avisoPendiente", j.getAvisoPendiente());
        return m;
    }
}
