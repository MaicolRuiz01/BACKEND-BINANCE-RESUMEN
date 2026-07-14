package com.binance.web.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.JornadaTrabajo;
import com.binance.web.Entity.Usuario;
import com.binance.web.Repository.JornadaTrabajoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Jornada de trabajo del operador: el botón "Empecé a trabajar" / "Terminé".
 * Mide el tiempo por el que efectivamente se le paga (distinto de la sesión con la app abierta).
 * El usuario se toma del token (principal autenticado), no del body.
 */
@RestController
@RequestMapping("/auth/jornada")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class JornadaController {

    private final JornadaTrabajoRepository jornadaRepository;

    /** Inicia una jornada. Si ya hay una en curso, la devuelve (idempotente). */
    @PostMapping("/iniciar")
    public ResponseEntity<Map<String, Object>> iniciar(@AuthenticationPrincipal Usuario user) {
        if (user == null) return ResponseEntity.status(401).build();

        JornadaTrabajo jornada = jornadaRepository
                .findFirstByUsernameAndEndedAtIsNullOrderByStartedAtDesc(user.getUsername())
                .orElse(null);

        if (jornada == null) {
            jornada = new JornadaTrabajo();
            jornada.setUsername(user.getUsername());
            jornada.setRol(user.getRol());
            jornada.setStartedAt(LocalDateTime.now());
            jornada = jornadaRepository.save(jornada);
        }
        return ResponseEntity.ok(toMap(jornada));
    }

    /** Termina la jornada en curso (si hay). */
    @PostMapping("/finalizar")
    public ResponseEntity<Map<String, Object>> finalizar(@AuthenticationPrincipal Usuario user) {
        if (user == null) return ResponseEntity.status(401).build();

        JornadaTrabajo jornada = jornadaRepository
                .findFirstByUsernameAndEndedAtIsNullOrderByStartedAtDesc(user.getUsername())
                .orElse(null);

        if (jornada != null) {
            jornada.setEndedAt(LocalDateTime.now());
            jornada = jornadaRepository.save(jornada);
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

    private Map<String, Object> toMap(JornadaTrabajo j) {
        boolean activa = j.getEndedAt() == null;
        LocalDateTime fin = activa ? LocalDateTime.now() : j.getEndedAt();
        long seg = j.getStartedAt() != null ? Math.max(0, Duration.between(j.getStartedAt(), fin).getSeconds()) : 0;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("activa", activa);
        m.put("startedAt", j.getStartedAt() != null ? j.getStartedAt().toString() : null);
        m.put("endedAt", j.getEndedAt() != null ? j.getEndedAt().toString() : null);
        m.put("transcurridoSegundos", seg);
        return m;
    }
}
