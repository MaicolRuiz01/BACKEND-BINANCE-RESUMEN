package com.binance.web.auth;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.JornadaTrabajo;
import com.binance.web.Entity.TarifaConfig;
import com.binance.web.Entity.Usuario;
import com.binance.web.Repository.JornadaTrabajoRepository;
import com.binance.web.Repository.TarifaConfigRepository;
import com.binance.web.Repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Panel de OPERADORES (solo ADMIN): una tarjeta por usuario con
 *  - tiempo trabajado en el día (jornadas "Empecé a trabajar"),
 *  - pago del día (horas * tarifa por hora),
 *  - credenciales (usuario + clave en texto plano) para compartir.
 * Además, la configuración de la tarifa por hora.
 */
@RestController
@RequestMapping("/auth/operadores")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OperadorController {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final double TARIFA_DEFECTO = 7500.0;
    private static final Integer TARIFA_ID = 1;

    private final UsuarioRepository usuarioRepository;
    private final JornadaTrabajoRepository jornadaRepository;
    private final TarifaConfigRepository tarifaRepository;

    /**
     * Resumen del día: una fila por usuario con tiempo trabajado, pago y credenciales.
     * @param fecha YYYY-MM-DD; por defecto hoy (America/Bogota).
     */
    @GetMapping("/resumen")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> resumen(@RequestParam(required = false) String fecha) {
        LocalDate dia = (fecha != null && !fecha.isBlank()) ? LocalDate.parse(fecha) : LocalDate.now(ZONE);
        LocalDateTime inicio = dia.atStartOfDay();
        LocalDateTime fin = dia.plusDays(1).atStartOfDay();
        LocalDateTime ahora = LocalDateTime.now();

        double valorHora = getTarifaValor();

        // Jornadas del día agrupadas por usuario (segundos trabajados) y quién sigue activo.
        List<JornadaTrabajo> jornadas = jornadaRepository.findByStartedAtBetween(inicio, fin);
        Map<String, Long> segundosPorUsuario = new HashMap<>();
        Map<String, Boolean> activaPorUsuario = new HashMap<>();
        for (JornadaTrabajo j : jornadas) {
            LocalDateTime finJ = j.getEndedAt() != null ? j.getEndedAt() : ahora;
            long seg = j.getStartedAt() != null ? Math.max(0, Duration.between(j.getStartedAt(), finJ).getSeconds()) : 0;
            segundosPorUsuario.merge(j.getUsername(), seg, Long::sum);
            if (j.getEndedAt() == null) activaPorUsuario.put(j.getUsername(), true);
        }

        List<Map<String, Object>> resultado = new ArrayList<>();
        usuarioRepository.findAll().stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .forEach(u -> {
                    long seg = segundosPorUsuario.getOrDefault(u.getUsername(), 0L);
                    double horas = seg / 3600.0;
                    double pago = horas * valorHora;

                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("rol", u.getRol() != null ? u.getRol().name() : null);
                    m.put("passwordPlano", u.getPasswordPlano());
                    m.put("tiempoTrabajadoSegundos", seg);
                    m.put("pagoCop", Math.round(pago));
                    m.put("jornadaActiva", activaPorUsuario.getOrDefault(u.getUsername(), false));
                    resultado.add(m);
                });
        return ResponseEntity.ok(resultado);
    }

    /** Tarifa por hora actual (COP). */
    @GetMapping("/tarifa")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getTarifa() {
        return ResponseEntity.ok(Map.of("valorHora", getTarifaValor()));
    }

    /** Actualiza la tarifa por hora (COP). Body: { "valorHora": 7500 }. */
    @PutMapping("/tarifa")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> setTarifa(@RequestBody Map<String, Double> body) {
        Double valor = body != null ? body.get("valorHora") : null;
        if (valor == null || valor < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "valorHora inválido"));
        }
        TarifaConfig cfg = tarifaRepository.findById(TARIFA_ID)
                .orElseGet(() -> new TarifaConfig(TARIFA_ID, TARIFA_DEFECTO));
        cfg.setValorHora(valor);
        tarifaRepository.save(cfg);
        return ResponseEntity.ok(Map.of("valorHora", valor));
    }

    private double getTarifaValor() {
        return tarifaRepository.findById(TARIFA_ID)
                .map(TarifaConfig::getValorHora)
                .orElse(TARIFA_DEFECTO);
    }
}
