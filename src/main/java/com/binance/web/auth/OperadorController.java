package com.binance.web.auth;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.Gasto;
import com.binance.web.Entity.JornadaTrabajo;
import com.binance.web.Entity.PagoOperador;
import com.binance.web.Entity.TarifaConfig;
import com.binance.web.Entity.Usuario;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.JornadaTrabajoRepository;
import com.binance.web.Repository.PagoOperadorRepository;
import com.binance.web.Repository.TarifaConfigRepository;
import com.binance.web.Repository.UsuarioRepository;
import com.binance.web.service.GastoService;

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
    private final PagoOperadorRepository pagoOperadorRepository;
    private final GastoService gastoService;
    private final AccountCopRepository accountCopRepository;
    private final EfectivoRepository efectivoRepository;

    /**
     * Resumen del día: una fila por usuario con tiempo trabajado, pago y credenciales.
     * @param fecha YYYY-MM-DD; por defecto hoy (America/Bogota).
     */
    @GetMapping("/resumen")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> resumen(@RequestParam(required = false) String fecha) {
        LocalDate dia = (fecha != null && !fecha.isBlank()) ? LocalDate.parse(fecha) : LocalDate.now(ZONE);

        double valorHora = getTarifaValor();

        // Segundos trabajados SOLO en el día consultado (para el pago de ese día).
        List<JornadaTrabajo> jornadas = jornadaRepository.findByStartedAtBetween(dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
        Map<String, Long> segundosPorUsuario = new HashMap<>();
        for (JornadaTrabajo j : jornadas) {
            LocalDateTime finJ = j.getEndedAt() != null ? j.getEndedAt() : LocalDateTime.now(ZONE);
            long seg = j.getStartedAt() != null ? Math.max(0, Duration.between(j.getStartedAt(), finJ).getSeconds()) : 0;
            segundosPorUsuario.merge(j.getUsername(), seg, Long::sum);
        }

        // "Activo" = tiene CUALQUIER jornada abierta (está trabajando ahora), sin importar el día.
        // Así concuerda con el endpoint de iniciar/terminar y el botón no se revierte al recargar.
        Set<String> activos = new HashSet<>();
        for (JornadaTrabajo abierta : jornadaRepository.findByEndedAtIsNull()) {
            activos.add(abierta.getUsername());
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
                    m.put("jornadaActiva", activos.contains(u.getUsername()));
                    // ¿Ya se le pagó ese día? Para bloquear el botón "Pagar" y evitar doble pago.
                    m.put("pagadoHoy", pagoOperadorRepository.existsByUsernameAndDia(u.getUsername(), dia));
                    resultado.add(m);
                });
        return ResponseEntity.ok(resultado);
    }

    /** Segundos trabajados por un usuario en un día concreto (misma lógica que el resumen). */
    private long segundosTrabajados(String username, LocalDate dia) {
        long total = 0;
        for (JornadaTrabajo j : jornadaRepository.findByStartedAtBetween(dia.atStartOfDay(), dia.plusDays(1).atStartOfDay())) {
            if (!username.equals(j.getUsername()) || j.getStartedAt() == null) continue;
            LocalDateTime finJ = j.getEndedAt() != null ? j.getEndedAt() : LocalDateTime.now(ZONE);
            total += Math.max(0, Duration.between(j.getStartedAt(), finJ).getSeconds());
        }
        return total;
    }

    /**
     * ADMIN: inicia la jornada de un operador (arranca su cronómetro), sin necesidad de que él
     * mismo le dé al botón. Idempotente: si ya tiene una jornada en curso, la devuelve.
     * Body opcional: { "modo": "VENTA_USDT" | "CAJA" }.
     */
    @PutMapping("/{id}/jornada/iniciar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> iniciarJornadaDe(@org.springframework.web.bind.annotation.PathVariable Integer id,
                                              @RequestBody(required = false) Map<String, String> body) {
        Usuario u = usuarioRepository.findById(id).orElse(null);
        if (u == null) return ResponseEntity.status(404).body(Map.of("error", "Operador no encontrado"));

        LocalDateTime ahora = LocalDateTime.now(ZONE);
        LocalDateTime inicioHoy = LocalDate.now(ZONE).atStartOfDay();

        JornadaTrabajo abierta = jornadaRepository
                .findFirstByUsernameAndEndedAtIsNullOrderByStartedAtDesc(u.getUsername())
                .orElse(null);

        // ¿La jornada abierta es de HOY? El resumen solo cuenta las de hoy, así que una jornada
        // abierta de un día anterior (p.ej. que quedó sin cerrar) haría que el botón nunca
        // cambiara: la cerramos y arrancamos una nueva de hoy para que todo quede consistente.
        boolean abiertaEsDeHoy = abierta != null && abierta.getStartedAt() != null
                && !abierta.getStartedAt().isBefore(inicioHoy);

        if (abierta != null && !abiertaEsDeHoy) {
            abierta.setEndedAt(ahora);
            jornadaRepository.save(abierta);
            abierta = null;
        }

        JornadaTrabajo jornada = abiertaEsDeHoy ? abierta : null;
        if (jornada == null) {
            jornada = new JornadaTrabajo();
            jornada.setUsername(u.getUsername());
            jornada.setRol(u.getRol());
            jornada.setStartedAt(ahora);
            jornada.setModo(parseModo(body));
            jornada = jornadaRepository.save(jornada);
        }

        // Deja SOLO esta jornada abierta: cierra cualquier otra huérfana del mismo usuario
        // (duplicados que quedaron de intentos previos), para que el conteo no se infle.
        final Integer vigenteId = jornada.getId();
        for (JornadaTrabajo otra : jornadaRepository.findByUsernameAndEndedAtIsNull(u.getUsername())) {
            if (!otra.getId().equals(vigenteId)) {
                otra.setEndedAt(ahora);
                jornadaRepository.save(otra);
            }
        }
        // OJO: Map.of NO admite valores null; el modo puede ser null (admin inicia sin modo),
        // así que se arma con HashMap para no reventar con NullPointerException.
        Map<String, Object> resp = new HashMap<>();
        resp.put("username", u.getUsername());
        resp.put("activa", true);
        resp.put("modo", jornada.getModo() != null ? jornada.getModo().name() : null);
        resp.put("startedAt", jornada.getStartedAt().toString());
        return ResponseEntity.ok(resp);
    }

    /** ADMIN: termina la jornada en curso de un operador (detiene su cronómetro). */
    @PutMapping("/{id}/jornada/finalizar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> finalizarJornadaDe(@org.springframework.web.bind.annotation.PathVariable Integer id) {
        Usuario u = usuarioRepository.findById(id).orElse(null);
        if (u == null) return ResponseEntity.status(404).body(Map.of("error", "Operador no encontrado"));

        // Cierra TODAS las jornadas abiertas del operador (por si quedó más de una huérfana),
        // así "Terminar" deja al operador realmente inactivo de una sola vez.
        LocalDateTime ahora = LocalDateTime.now(ZONE);
        for (JornadaTrabajo abierta : jornadaRepository.findByUsernameAndEndedAtIsNull(u.getUsername())) {
            abierta.setEndedAt(ahora);
            jornadaRepository.save(abierta);
        }
        return ResponseEntity.ok(Map.of("username", u.getUsername(), "activa", false));
    }

    /**
     * ADMIN: paga el día trabajado de un operador. Calcula (horas * tarifa), genera un Gasto
     * ("Pago a operador X") que resta de la cuenta COP o caja elegida, y guarda el registro
     * en el historial. Bloquea el doble pago del mismo día.
     * Body: { "fecha": "YYYY-MM-DD" (opcional, def hoy), "cuentaCopId": n | "cajaId": n }.
     */
    @PutMapping("/{id}/pagar")
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> pagarOperador(@org.springframework.web.bind.annotation.PathVariable Integer id,
                                           @RequestBody(required = false) Map<String, Object> body) {
        Usuario u = usuarioRepository.findById(id).orElse(null);
        if (u == null) return ResponseEntity.status(404).body(Map.of("message", "Operador no encontrado"));

        String fechaStr = body != null && body.get("fecha") != null ? String.valueOf(body.get("fecha")) : null;
        LocalDate dia = (fechaStr != null && !fechaStr.isBlank()) ? LocalDate.parse(fechaStr) : LocalDate.now(ZONE);

        // Bloqueo de doble pago del mismo día.
        if (pagoOperadorRepository.existsByUsernameAndDia(u.getUsername(), dia)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya se le pagó a " + u.getUsername() + " ese día."));
        }

        // No se puede pagar el día de hoy si la jornada sigue activa (hay que detenerla primero).
        boolean hayJornadaActiva = !jornadaRepository.findByUsernameAndEndedAtIsNull(u.getUsername()).isEmpty();
        if (dia.equals(LocalDate.now(ZONE)) && hayJornadaActiva) {
            return ResponseEntity.badRequest().body(Map.of("message", "Detén la jornada del operador antes de pagar."));
        }

        long seg = segundosTrabajados(u.getUsername(), dia);
        if (seg <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "El operador no tiene tiempo trabajado ese día."));
        }

        double tarifa = getTarifaValor();
        double monto = Math.round((seg / 3600.0) * tarifa);
        if (monto <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "El monto a pagar es 0."));
        }

        Integer cuentaCopId = toInteger(body != null ? body.get("cuentaCopId") : null);
        Integer cajaId      = toInteger(body != null ? body.get("cajaId") : null);

        Gasto gasto = new Gasto();
        gasto.setDescripcion("Pago a operador " + u.getUsername());
        gasto.setMonto(monto);
        // Idempotencia: una por operador+día → un segundo clic no genera otro gasto ni resta doble.
        gasto.setIdempotencyKey("PAGO-OP-" + u.getUsername() + "-" + dia);

        String origen;
        if (cuentaCopId != null) {
            AccountCop c = new AccountCop();
            c.setId(cuentaCopId);
            gasto.setCuentaPago(c);
            origen = "Cuenta: " + accountCopRepository.findById(cuentaCopId).map(AccountCop::getName).orElse(String.valueOf(cuentaCopId));
        } else if (cajaId != null) {
            Efectivo e = new Efectivo();
            e.setId(cajaId);
            gasto.setPagoEfectivo(e);
            origen = "Caja: " + efectivoRepository.findById(cajaId).map(Efectivo::getName).orElse(String.valueOf(cajaId));
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "Elige de qué cuenta o caja sale el pago."));
        }

        Gasto guardado = gastoService.saveGasto(gasto); // resta el saldo y registra el movimiento

        PagoOperador pago = new PagoOperador();
        pago.setUsername(u.getUsername());
        pago.setDia(dia);
        pago.setFecha(LocalDateTime.now(ZONE));
        pago.setSegundos(seg);
        pago.setTarifaHora(tarifa);
        pago.setMonto(monto);
        pago.setGastoId(guardado.getId());
        pago.setOrigen(origen);
        pagoOperadorRepository.save(pago);

        return ResponseEntity.ok(pago);
    }

    /** ADMIN: historial de pagos de un operador (del más reciente al más antiguo). */
    @GetMapping("/{id}/pagos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> historialPagos(@org.springframework.web.bind.annotation.PathVariable Integer id) {
        Usuario u = usuarioRepository.findById(id).orElse(null);
        if (u == null) return ResponseEntity.status(404).body(Map.of("message", "Operador no encontrado"));
        return ResponseEntity.ok(pagoOperadorRepository.findByUsernameOrderByFechaDesc(u.getUsername()));
    }

    /** Convierte un valor del body (Number o String) a Integer; null si no es un número válido. */
    private Integer toInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (NumberFormatException e) { return null; }
    }

    /** Lee el modo del body de forma tolerante: vacío/ inválido → null. */
    private com.binance.web.Entity.ModoJornada parseModo(Map<String, String> body) {
        if (body == null) return null;
        String raw = body.get("modo");
        if (raw == null || raw.isBlank()) return null;
        try {
            return com.binance.web.Entity.ModoJornada.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
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
