package com.binance.web.BinanceAPI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.AutoAsignacionConfig;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.AutoAsignacionConfigRepository;
import com.binance.web.activacion.CuentaP2PSyncService;
import com.binance.web.dto.ActiveP2POrderDto;
import com.binance.web.util.CupoDiarioRules;

import lombok.extern.slf4j.Slf4j;

/**
 * Asignación automática de cuentas COP a las ventas P2P EN CURSO.
 *
 * Cuando el interruptor está encendido, el sistema pre-asigna solo una cuenta COP a cada
 * venta en curso que va apareciendo, siguiendo las reglas que pidió el cliente:
 *
 *  1) Se asigna primero a las cuentas COP MÁS CERCANAS a su límite (menor cupo disponible),
 *     siempre que la venta quepa.
 *  2) Una venta "cabe" si tras asignarla la cuenta no se pasa del cupo por más de {@link #TOLERANCIA}
 *     (el cliente permite pasarse "solo un poquito": hasta $50.000).
 *  3) Si una cuenta agota su cupo (disponible ≤ −TOLERANCIA), se desactiva de P2P
 *     (avisando al bot vía {@link CuentaP2PSyncService}) y se ACTIVA la siguiente candidata con cupo.
 *
 * Unidades: todos los montos van en MILES de COP (igual que pesosCop y los cupos diarios),
 * por eso la tolerancia de $50.000 es 50.0 aquí.
 *
 * El motor corre en el backend (lo dispara el poll de órdenes activas cada 15 s) para que
 * agarre las ventas aunque nadie tenga la vista abierta. Solo actúa si el interruptor está ON.
 */
@Slf4j
@Service
public class AsignacionAutomaticaService {

    /** Una cuenta puede pasarse del cupo hasta este monto (MILES de COP = $50.000). */
    private static final double TOLERANCIA = 50.0;
    /** Cupo restante mínimo (MILES) para activar una cuenta nueva como reemplazo. */
    private static final double SUBLIMITE_ACTIVAR = 1_000.0;
    private static final Integer CONFIG_ID = 1;

    @Autowired private P2PActiveOrderService activeOrderService;
    @Autowired private AccountCopRepository accountCopRepository;
    @Autowired private CuentaP2PSyncService cuentaP2PSyncService;
    @Autowired private AutoAsignacionConfigRepository configRepository;
    @Autowired @Lazy private AsignacionAutomaticaService self;

    /** Evita que dos ciclos se solapen (el poll corre cada 15 s). */
    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    // ── Interruptor ───────────────────────────────────────────────

    public boolean isActiva() {
        return configRepository.findById(CONFIG_ID)
                .map(AutoAsignacionConfig::getActiva)
                .orElse(false);
    }

    @Transactional
    public boolean setActiva(boolean activa) {
        AutoAsignacionConfig cfg = configRepository.findById(CONFIG_ID)
                .orElseGet(() -> new AutoAsignacionConfig(CONFIG_ID, false));
        cfg.setActiva(activa);
        configRepository.save(cfg);
        log.info("[AutoAsign] Asignación automática {}", activa ? "ACTIVADA" : "DESACTIVADA");
        return activa;
    }

    // ── Motor ─────────────────────────────────────────────────────

    /**
     * Punto de entrada del scheduler. La lectura de órdenes a Binance se hace FUERA de la
     * transacción (no retiene conexión de BD durante la llamada HTTP lenta); la parte que
     * escribe en BD va en {@link #asignar(List)} (transaccional).
     */
    public void ejecutar() {
        if (!isActiva()) return;
        if (!enCurso.compareAndSet(false, true)) return; // ya hay un ciclo corriendo
        try {
            List<ActiveP2POrderDto> ordenes;
            try {
                ordenes = activeOrderService.getAllActiveOrders();
            } catch (Exception e) {
                log.warn("[AutoAsign] No se pudieron leer las órdenes activas: {}", e.getMessage());
                return;
            }
            if (ordenes == null || ordenes.isEmpty()) return;
            self.asignar(ordenes);
        } catch (Exception e) {
            log.warn("[AutoAsign] Error en el ciclo de asignación automática: {}", e.getMessage());
        } finally {
            enCurso.set(false);
        }
    }

    @Transactional
    public void asignar(List<ActiveP2POrderDto> ordenes) {
        // Órdenes sin cuenta asignada, más antiguas primero.
        List<ActiveP2POrderDto> pendientes = ordenes.stream()
                .filter(o -> o.getPreAsignadoCopId() == null)
                .sorted(Comparator.comparing(ActiveP2POrderDto::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        if (pendientes.isEmpty()) return;

        // Cuentas COP en memoria, con los cupos del día al día.
        List<AccountCop> todas = accountCopRepository.findAll();
        todas.stream().filter(a -> a.getBankType() != null).forEach(CupoDiarioRules::asegurarCupoHoy);

        // "Comprometido" = pesosCop de las ventas EN CURSO ya pre-asignadas a cada cuenta.
        // Aún no se importaron, así que todavía no bajaron el balance: hay que restarlo aparte
        // para no sobre-asignar la misma cuenta.
        Map<Integer, Double> comprometido = new HashMap<>();
        for (ActiveP2POrderDto o : ordenes) {
            if (o.getPreAsignadoCopId() != null) {
                comprometido.merge(o.getPreAsignadoCopId(), val(o.getPesosCop()), Double::sum);
            }
        }

        List<AccountCop> cambiadas = new ArrayList<>();

        for (ActiveP2POrderDto o : pendientes) {
            double monto = val(o.getPesosCop());
            AccountCop elegida = elegirCuenta(todas, comprometido, monto);
            if (elegida == null) {
                log.info("[AutoAsign] Sin cuenta con cupo para la orden {} ({} miles).", o.getOrderNumber(), monto);
                continue;
            }

            try {
                activeOrderService.upsertPreAsignacion(o.getOrderNumber(), elegida.getId(), o.getAccountBinance());
            } catch (Exception e) {
                log.warn("[AutoAsign] No se pudo asignar la orden {} a {}: {}",
                        o.getOrderNumber(), elegida.getName(), e.getMessage());
                continue;
            }
            comprometido.merge(elegida.getId(), monto, Double::sum);
            log.info("[AutoAsign] Orden {} → {} (disponible restante {} miles).",
                    o.getOrderNumber(), elegida.getName(), disponible(elegida, comprometido));

            // ¿Agotó su cupo? → desactivar y activar la siguiente candidata.
            if (disponible(elegida, comprometido) <= -TOLERANCIA) {
                desactivar(elegida);
                cambiadas.add(elegida);
                AccountCop siguiente = activarSiguiente(todas);
                if (siguiente != null) cambiadas.add(siguiente);
            }
        }

        if (!cambiadas.isEmpty()) accountCopRepository.saveAll(cambiadas);
    }

    // ── Selección ─────────────────────────────────────────────────

    /** Entre las cuentas ACTIVAS donde la venta quepa, la MÁS cercana al límite (menor disponible). */
    private AccountCop elegirCuenta(List<AccountCop> todas, Map<Integer, Double> comprometido, double monto) {
        return todas.stream()
                .filter(a -> a.getId() != null)
                .filter(a -> Boolean.TRUE.equals(a.getActivaParaP2P()))
                .filter(a -> !Boolean.TRUE.equals(a.getBloqueada()))
                .filter(a -> a.getBankType() != null)
                .filter(a -> disponible(a, comprometido) - monto >= -TOLERANCIA) // cabe (hasta 50k de exceso)
                .min(Comparator.comparingDouble(a -> disponible(a, comprometido)))
                .orElse(null);
    }

    /** Activa la siguiente candidata (inactiva, con cupo), la más cercana al límite — igual que la selección de las 5. */
    private AccountCop activarSiguiente(List<AccountCop> todas) {
        AccountCop next = todas.stream()
                .filter(a -> a.getId() != null)
                .filter(a -> !Boolean.TRUE.equals(a.getActivaParaP2P()))
                .filter(a -> !Boolean.TRUE.equals(a.getBloqueada()))
                .filter(a -> a.getBankType() != null)
                .filter(a -> (max(a) - bal(a)) >= SUBLIMITE_ACTIVAR)
                .min(Comparator.comparingDouble(a -> max(a) - bal(a)))
                .orElse(null);
        if (next == null) {
            log.info("[AutoAsign] No hay más cuentas candidatas con cupo para activar.");
            return null;
        }
        boolean antes = Boolean.TRUE.equals(next.getActivaParaP2P());
        next.setActivaParaP2P(true);
        cuentaP2PSyncService.sincronizar(next, antes);
        log.info("[AutoAsign] Activada la siguiente cuenta COP: {}", next.getName());
        return next;
    }

    private void desactivar(AccountCop acc) {
        boolean antes = Boolean.TRUE.equals(acc.getActivaParaP2P());
        acc.setActivaParaP2P(false);
        cuentaP2PSyncService.sincronizar(acc, antes);
        log.info("[AutoAsign] {} agotó su cupo → desactivada de P2P.", acc.getName());
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** Cupo disponible proyectado (MILES) = tope diario − balance − ventas en curso ya asignadas. */
    private double disponible(AccountCop acc, Map<Integer, Double> comprometido) {
        return max(acc) - bal(acc) - comprometido.getOrDefault(acc.getId(), 0.0);
    }

    private double max(AccountCop a) { return a.getCupoDiarioMax() != null ? a.getCupoDiarioMax() : 0.0; }
    private double bal(AccountCop a) { return a.getBalance() != null ? a.getBalance() : 0.0; }
    private double val(Double d)     { return d != null ? d : 0.0; }
}
