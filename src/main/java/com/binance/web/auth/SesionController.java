package com.binance.web.auth;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.SesionOperador;
import com.binance.web.Repository.SesionOperadorRepository;

import lombok.RequiredArgsConstructor;

/**
 * Seguimiento de sesiones de operadores:
 *  - heartbeat: el frontend lo llama cada ~1 min mientras la app está abierta.
 *  - logout: marca el fin de la sesión.
 *  - resumen: reporte por día (solo ADMIN) con ingresos y tiempo total por operador.
 */
@RestController
@RequestMapping("/auth/sesion")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SesionController {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final SesionOperadorRepository sesionRepository;

    /** Latido: actualiza lastSeenAt mientras la sesión siga abierta. */
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestBody Map<String, Integer> body) {
        Integer sessionId = body != null ? body.get("sessionId") : null;
        if (sessionId != null) {
            sesionRepository.findById(sessionId).ifPresent(s -> {
                if (s.getLogoutAt() == null) {
                    s.setLastSeenAt(LocalDateTime.now());
                    sesionRepository.save(s);
                }
            });
        }
        return ResponseEntity.ok().build();
    }

    /** Cierre de sesión: fija logoutAt (best-effort; también lo llama el sendBeacon al cerrar pestaña). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, Integer> body) {
        Integer sessionId = body != null ? body.get("sessionId") : null;
        if (sessionId != null) {
            sesionRepository.findById(sessionId).ifPresent(s -> {
                LocalDateTime ahora = LocalDateTime.now();
                if (s.getLogoutAt() == null) s.setLogoutAt(ahora);
                s.setLastSeenAt(ahora);
                sesionRepository.save(s);
            });
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Resumen por día (solo ADMIN). Por cada operador que tuvo sesiones ese día:
     * número de ingresos y tiempo total (segundos) en la app.
     * @param fecha formato YYYY-MM-DD; si no viene, se usa el día de hoy (America/Bogota).
     */
    @GetMapping("/resumen")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> resumen(@RequestParam(required = false) String fecha) {
        LocalDate dia = (fecha != null && !fecha.isBlank()) ? LocalDate.parse(fecha) : LocalDate.now(ZONE);
        LocalDateTime inicio = dia.atStartOfDay();
        LocalDateTime fin = dia.plusDays(1).atStartOfDay();

        List<SesionOperador> sesiones = sesionRepository.findByLoginAtBetween(inicio, fin);

        // "En línea" = sin logout Y con un heartbeat reciente (últimos ~3 min).
        // Así una pestaña cerrada (heartbeat viejo) NO queda marcada como online para siempre.
        LocalDateTime umbralOnline = LocalDateTime.now().minusMinutes(3);

        // Agrupar por username, acumulando ingresos y tiempo total.
        Map<String, Agg> porUsuario = new LinkedHashMap<>();
        for (SesionOperador s : sesiones) {
            LocalDateTime finSesion = s.getLogoutAt() != null ? s.getLogoutAt() : s.getLastSeenAt();
            long segundos = 0;
            if (s.getLoginAt() != null && finSesion != null) {
                segundos = Math.max(0, Duration.between(s.getLoginAt(), finSesion).getSeconds());
            }

            Agg acc = porUsuario.computeIfAbsent(s.getUsername(), u -> new Agg(u,
                    s.getRol() != null ? s.getRol().name() : null));
            acc.ingresos += 1;
            acc.tiempoTotalSegundos += segundos;
            if (s.getLogoutAt() == null && s.getLastSeenAt() != null
                    && s.getLastSeenAt().isAfter(umbralOnline)) {
                acc.sesionAbierta = true;
            }
        }

        List<Map<String, Object>> resultado = new ArrayList<>();
        porUsuario.values().stream()
                .sorted(Comparator.comparing((Agg a) -> a.username))
                .forEach(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("username", a.username);
                    m.put("rol", a.rol);
                    m.put("ingresos", a.ingresos);
                    m.put("tiempoTotalSegundos", a.tiempoTotalSegundos);
                    m.put("sesionAbierta", a.sesionAbierta);
                    resultado.add(m);
                });
        return ResponseEntity.ok(resultado);
    }

    /** Acumulador por operador para el resumen. */
    private static class Agg {
        final String username;
        final String rol;
        int ingresos = 0;
        long tiempoTotalSegundos = 0;
        boolean sesionAbierta = false;

        Agg(String username, String rol) {
            this.username = username;
            this.rol = rol;
        }
    }
}
