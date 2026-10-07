package com.binance.web.BinanceAPI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.binance.web.Repository.P2PPreAsignacionRepository;
import com.binance.web.Entity.BankType;
import com.binance.web.activacion.CuentaP2PSyncService;
import com.binance.web.movimientosbridge.MovimientosCuentasPendientes;
import com.binance.web.service.AccountCopService;
import com.binance.web.dto.ActiveP2POrderDto;
import com.binance.web.util.CupoDiarioRules;
import com.binance.web.util.VentanaCupoP2P;
import com.binance.web.util.VentanaCupoP2P.Canal;

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
 *     (el cliente permite pasarse hasta $500.000: acordado en las llamadas; antes estaba en $50.000 por
 *     error y por eso las cuentas casi llenas no recibían las ventas que las completaban).
 *  3) Si una cuenta agota su cupo (disponible ≤ −TOLERANCIA), se desactiva de P2P
 *     (avisando al bot vía {@link CuentaP2PSyncService}) y se ACTIVA la siguiente candidata con cupo.
 *  4) El cupo que cuenta depende de la HORA ({@link VentanaCupoP2P}): de 00:00 a 18:29 solo el de
 *     CORRESPONSAL y de 18:30 a 23:59 solo el de CAJERO. El otro canal se ignora.
 *  5) SELECCIÓN DE CUENTAS: si NO hay ninguna cuenta activa en P2P, el Auto elige y activa las 7 más
 *     cercanas al límite del canal de la hora (AccountCopService.activarCincoCuentasMasCercanasAlCupo),
 *     lo que además le avisa a Movimientos para que las abra. Pasa al prenderlo y, si ya está
 *     prendido, cuando llega una venta y no queda ninguna cuenta activa. Si ya hay cuentas activas
 *     (aunque sean pocas) NO elige otras: trabaja con las que están.
 *  6) SOLO BANCOLOMBIA: es el único banco que Movimientos monitorea, así que solo esas cuentas se
 *     eligen y reciben ventas del Auto (por ahora).
 *  7) CONFIRMADAS: una cuenta recién activada no recibe ventas hasta que Movimientos confirme que la
 *     abrió (evento "conexion_exitosa", ver {@link MovimientosCuentasPendientes}).
 *  8) CUENTA LLENA: una cuenta sale del grupo (se desactiva y se reemplaza por otra candidata) cuando
 *     su cupo del canal ya se cumplió con plata REAL (cupo − saldo ≤ 0) y NO le queda ninguna venta
 *     abierta. Mientras tenga ventas abiertas se asume que esa plata ya llegó (no recibe más ventas
 *     que no quepan) pero sigue activa y monitoreada hasta que esas ventas se cierren: si una se
 *     cae, la cuenta recupera espacio; si se libera, ahí sí sale. Se revisa en cada ciclo, haya o
 *     no ventas por asignar.
 *  9) TIPO DE CUPO: las cuentas que el Auto elige o repone quedan marcadas (cupoTipoP2P) con el canal
 *     de la hora. Las que YA estaban activas no se tocan, ni siquiera cuando la ventana cambia.
 *     OJO: ese tipo decide por qué canal se dispara el retiro automático de la cuenta.
 * 10) NO REPETIDAS: una cuenta que ya tiene una venta EN CURSO por el mismo monto exacto no recibe
 *     otra igual (el depósito sería indistinguible en Movimientos). Vale mientras la primera no se
 *     cierre: al liberarse sale de la lista de órdenes en curso y el monto vuelve a estar disponible.
 *
 * Unidades: todos los montos van en MILES de COP (igual que pesosCop y los cupos diarios),
 * por eso la tolerancia de $500.000 es 500.0 aquí.
 *
 * El motor corre en el backend (lo dispara el poll de órdenes activas cada 15 s) para que
 * agarre las ventas aunque nadie tenga la vista abierta. Solo actúa si el interruptor está ON.
 */
@Slf4j
@Service
public class AsignacionAutomaticaService {

    /** Una cuenta puede pasarse del cupo hasta este monto (MILES de COP = $500.000). */
    private static final double TOLERANCIA = 500.0;
    /** Cupo restante mínimo (MILES) para activar una cuenta nueva como reemplazo. */
    private static final double SUBLIMITE_ACTIVAR = 1_000.0;
    private static final Integer CONFIG_ID = 1;
    /** Una cuenta está "llena" cuando su espacio real en el canal (cupo − saldo, MILES) llega a esto o menos. */
    private static final double LIMITE_LLENA = 0.0;

    @Autowired private P2PActiveOrderService activeOrderService;
    @Autowired private AccountCopRepository accountCopRepository;
    @Autowired private CuentaP2PSyncService cuentaP2PSyncService;
    @Autowired private AutoAsignacionConfigRepository configRepository;
    /** Ventas con cuenta pre-asignada cuya venta aún no se importó (las que siguen "en curso"). */
    @Autowired private P2PPreAsignacionRepository preAsignacionRepository;
    /** Cuentas pedidas a Movimientos y aun sin confirmar: no reciben ventas hasta que confirme. */
    @Autowired private MovimientosCuentasPendientes cuentasPendientes;
    /** @Lazy: evita un ciclo de dependencias; solo se usa para elegir las cuentas iniciales. */
    @Autowired @Lazy private AccountCopService accountCopService;
    @Autowired @Lazy private AsignacionAutomaticaService self;

    /** Reloj para la regla horaria (en pruebas se reemplaza para fijar la hora). */
    private java.time.Clock reloj = java.time.Clock.system(VentanaCupoP2P.ZONA);

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
        if (activa) {
            // Al prenderlo sin ninguna cuenta seleccionada, las elige. Si falla, el interruptor igual queda ON:
            // el ciclo lo reintentará cuando llegue una venta.
            try {
                seleccionarCuentasSiNoHay(accountCopRepository.findAll());
            } catch (Exception e) {
                log.warn("[AutoAsign] No se pudieron elegir las cuentas al activar: {}", e.getMessage());
            }
        }
        return activa;
    }

    /** ¿Hay al menos una cuenta activa en P2P (y no bloqueada)? */
    private static boolean hayCuentasActivas(List<AccountCop> todas) {
        return todas.stream().anyMatch(a -> Boolean.TRUE.equals(a.getActivaParaP2P())
                && !Boolean.TRUE.equals(a.getBloqueada())
                && a.getBankType() == BankType.BANCOLOMBIA);
    }

    /**
     * Si no hay ninguna cuenta activa en P2P, elige y activa las 7 más cercanas al límite del canal
     * de la hora. Cada activación se le avisa a Movimientos (CuentaP2PSyncService). Si ya hay
     * cuentas activas no hace nada. Devuelve true si eligió cuentas.
     */
    private boolean seleccionarCuentasSiNoHay(List<AccountCop> todas) {
        if (hayCuentasActivas(todas)) return false;
        List<AccountCop> elegidas = accountCopService.activarCincoCuentasMasCercanasAlCupo();
        if (elegidas == null || elegidas.isEmpty()) {
            log.warn("[AutoAsign] No hay cuentas activas ni candidatas con cupo para elegir.");
            return false;
        }
        log.info("[AutoAsign] No había cuentas activas: se eligieron {} ({}).", elegidas.size(),
                elegidas.stream().map(AccountCop::getName).collect(Collectors.joining(", ")));
        return true;
    }

    // ── Motor ─────────────────────────────────────────────────────

    /**
     * Punto de entrada del scheduler. La lectura de órdenes a Binance se hace FUERA de la
     * transacción (no retiene conexión de BD durante la llamada HTTP lenta); la parte que
     * escribe en BD va en {@link #asignar(List)} (transaccional).
     */
    /**
     * @param ordenes las órdenes activas que el poll ACABA de leer. Antes este método volvía a
     *                pedírselas a Binance en cada tick (el doble de llamadas por cuenta cada 15 s).
     */
    public void ejecutar(List<ActiveP2POrderDto> ordenes) {
        if (!isActiva()) return;
        if (!enCurso.compareAndSet(false, true)) return; // ya hay un ciclo corriendo
        try {
            // Sin ventas en curso igual se revisa el grupo: una cuenta puede haberse llenado al liberarse su última venta.
            self.asignar(ordenes != null ? ordenes : List.of());
        } catch (Exception e) {
            log.warn("[AutoAsign] Error en el ciclo de asignación automática: {}", e.getMessage());
        } finally {
            enCurso.set(false);
        }
    }

    @Transactional
    public void asignar(List<ActiveP2POrderDto> ordenes) {
        // Cuentas COP en memoria, con los cupos del día al día.
        List<AccountCop> todas = accountCopRepository.findAll();
        todas.stream().filter(a -> a.getBankType() != null).forEach(CupoDiarioRules::asegurarCupoHoy);

        // Canal de la hora actual: solo su cupo cuenta (corresponsal de día, cajero desde 18:30).
        Canal canal = VentanaCupoP2P.canalAhora(reloj);
        List<AccountCop> cambiadas = new ArrayList<>();

        // 1) Mantener el grupo: sacar las cuentas ya llenas (y sin ventas abiertas) y reponerlas.
        mantenerGrupo(todas, ordenes, canal, cambiadas);

        // 2) Ventas sin cuenta asignada, más antiguas primero.
        List<ActiveP2POrderDto> pendientes = ordenes.stream()
                .filter(o -> o.getPreAsignadoCopId() == null)
                .sorted(Comparator.comparing(ActiveP2POrderDto::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        if (pendientes.isEmpty()) {
            if (!cambiadas.isEmpty()) accountCopRepository.saveAll(cambiadas);
            return;
        }

        // Sin ninguna cuenta activa no hay a quién asignar: se eligen y se vuelve a leer.
        if (seleccionarCuentasSiNoHay(todas)) {
            todas = accountCopRepository.findAll();
            todas.stream().filter(a -> a.getBankType() != null).forEach(CupoDiarioRules::asegurarCupoHoy);
        }

        // "Comprometido" = pesosCop de las ventas EN CURSO ya pre-asignadas a cada cuenta.
        // Aún no se importaron, así que todavía no bajaron el balance: hay que restarlo aparte
        // para no sobre-asignar la misma cuenta.
        Map<Integer, Double> comprometido = new HashMap<>();
        for (ActiveP2POrderDto o : ordenes) {
            if (o.getPreAsignadoCopId() != null) {
                comprometido.merge(o.getPreAsignadoCopId(), val(o.getPesosCop()), Double::sum);
            }
        }

        // Montos exactos de las ventas EN CURSO ya asignadas a cada cuenta (la lista solo trae
        // órdenes no finalizadas: una liberada o cancelada ya no está). Sirve para no repetir.
        Map<Integer, Set<Long>> montosAbiertos = new HashMap<>();
        for (ActiveP2POrderDto o : ordenes) {
            if (o.getPreAsignadoCopId() != null) {
                montosAbiertos.computeIfAbsent(o.getPreAsignadoCopId(), k -> new HashSet<>())
                        .add(claveMonto(val(o.getPesosCop())));
            }
        }

        for (ActiveP2POrderDto o : pendientes) {
            double monto = val(o.getPesosCop());
            AccountCop elegida = elegirCuenta(todas, comprometido, montosAbiertos, monto, canal);
            if (elegida == null) {
                log.info("[AutoAsign] Sin cuenta con cupo de {} (y sin otra orden igual abierta) para la orden {} ({} miles).",
                        canal, o.getOrderNumber(), monto);
                continue;
            }

            try {
                // El operador pudo asignarla a mano entre el poll y este momento: no pisarla.
                if (activeOrderService.tienePreAsignacion(o.getOrderNumber())) continue;
                activeOrderService.upsertPreAsignacion(o.getOrderNumber(), elegida.getId(),
                        o.getAccountBinance(), o.getPesosCop());
            } catch (Exception e) {
                log.warn("[AutoAsign] No se pudo asignar la orden {} a {}: {}",
                        o.getOrderNumber(), elegida.getName(), e.getMessage());
                continue;
            }
            comprometido.merge(elegida.getId(), monto, Double::sum);
            montosAbiertos.computeIfAbsent(elegida.getId(), k -> new HashSet<>()).add(claveMonto(monto));
            log.info("[AutoAsign] Orden {} → {} (cupo {} restante {} miles).",
                    o.getOrderNumber(), elegida.getName(), canal, disponible(elegida, comprometido, canal));
            // OJO: aunque esta venta la deje sin espacio, la cuenta NO se desactiva acá: tiene una venta
            // abierta y su plata todavía no llega. mantenerGrupo la saca cuando esa venta se cierre.
        }

        if (!cambiadas.isEmpty()) accountCopRepository.saveAll(cambiadas);
    }

    // ── Mantenimiento del grupo ───────────────────────────────────

    /** Cuentas que todavía esperan plata: tienen alguna venta en curso asignada. */
    private Set<Integer> cuentasConVentasAbiertas(List<ActiveP2POrderDto> ordenes) {
        Set<Integer> ids = new HashSet<>();
        for (ActiveP2POrderDto o : ordenes) {
            if (o.getPreAsignadoCopId() != null) ids.add(o.getPreAsignadoCopId());
        }
        // También las pre-asignaciones guardadas sin importar: la lista de órdenes puede venir
        // incompleta si Binance falló en este ciclo, y no se debe sacar una cuenta que espera plata.
        // (Incluye alguna huérfana de una orden cancelada: en ese caso la cuenta se queda un rato más.)
        for (var p : preAsignacionRepository.findSinImportar()) {
            if (p.getCopId() != null) ids.add(p.getCopId());
        }
        return ids;
    }

    /**
     * Saca del grupo las cuentas cuyo cupo del canal ya se cumplió con plata real y que no esperan
     * ninguna venta, y activa en su lugar la siguiente candidata (el grupo mantiene su tamaño).
     */
    private void mantenerGrupo(List<AccountCop> todas, List<ActiveP2POrderDto> ordenes,
                               Canal canal, List<AccountCop> cambiadas) {
        Set<Integer> conVentasAbiertas = null; // se calcula solo si hace falta (evita una consulta por ciclo)
        for (AccountCop a : new ArrayList<>(todas)) {
            if (a.getId() == null) continue;
            if (!Boolean.TRUE.equals(a.getActivaParaP2P()) || Boolean.TRUE.equals(a.getBloqueada())) continue;
            if (a.getBankType() != BankType.BANCOLOMBIA) continue;
            if (VentanaCupoP2P.cupoHoy(a, canal) - bal(a) > LIMITE_LLENA) continue; // todavía tiene espacio

            if (conVentasAbiertas == null) conVentasAbiertas = cuentasConVentasAbiertas(ordenes);
            if (conVentasAbiertas.contains(a.getId())) continue; // espera plata: se queda hasta que se cierre

            log.info("[AutoAsign] {} llegó al límite de {} sin ventas abiertas → sale del grupo.", a.getName(), canal);
            desactivar(a);
            cambiadas.add(a);
            AccountCop siguiente = activarSiguiente(todas, canal);
            if (siguiente != null) cambiadas.add(siguiente);
        }
    }

    // ── Selección ─────────────────────────────────────────────────

    /**
     * Entre las cuentas ACTIVAS donde la venta quepa, la MÁS cercana al límite (menor disponible),
     * descartando las que ya tienen una venta en curso por ese mismo monto exacto.
     */
    private AccountCop elegirCuenta(List<AccountCop> todas, Map<Integer, Double> comprometido,
                                    Map<Integer, Set<Long>> montosAbiertos, double monto, Canal canal) {
        long clave = claveMonto(monto);
        return todas.stream()
                .filter(a -> a.getId() != null)
                .filter(a -> Boolean.TRUE.equals(a.getActivaParaP2P()))
                .filter(a -> !Boolean.TRUE.equals(a.getBloqueada()))
                .filter(a -> a.getBankType() == BankType.BANCOLOMBIA) // solo Bancolombia (lo que Movimientos monitorea)
                .filter(a -> !cuentasPendientes.estaPendiente(a.getName())) // Movimientos ya confirmó que la abrió
                .filter(a -> !montosAbiertos.getOrDefault(a.getId(), Set.of()).contains(clave)) // no repetidas
                .filter(a -> disponible(a, comprometido, canal) - monto >= -TOLERANCIA) // cabe (hasta 500k de exceso)
                .min(Comparator.comparingDouble(a -> disponible(a, comprometido, canal)))
                .orElse(null);
    }

    /** Activa la siguiente candidata (inactiva, con cupo), la más cercana al límite — igual que la selección de las 7. */
    private AccountCop activarSiguiente(List<AccountCop> todas, Canal canal) {
        AccountCop next = todas.stream()
                .filter(a -> a.getId() != null)
                .filter(a -> !Boolean.TRUE.equals(a.getActivaParaP2P()))
                .filter(a -> !Boolean.TRUE.equals(a.getBloqueada()))
                .filter(a -> a.getBankType() == BankType.BANCOLOMBIA)
                .filter(a -> (VentanaCupoP2P.cupoHoy(a, canal) - bal(a)) >= SUBLIMITE_ACTIVAR)
                .min(Comparator.comparingDouble(a -> VentanaCupoP2P.cupoHoy(a, canal) - bal(a)))
                .orElse(null);
        if (next == null) {
            log.info("[AutoAsign] No hay más cuentas candidatas con cupo para activar.");
            return null;
        }
        boolean antes = Boolean.TRUE.equals(next.getActivaParaP2P());
        next.setActivaParaP2P(true);
        next.setCupoTipoP2P(canal.name());
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

    /**
     * Cupo disponible proyectado (MILES) = cupo de retiro que le queda HOY en el canal de la hora
     * − balance − ventas en curso ya asignadas.
     */
    private double disponible(AccountCop acc, Map<Integer, Double> comprometido, Canal canal) {
        return VentanaCupoP2P.cupoHoy(acc, canal) - bal(acc) - comprometido.getOrDefault(acc.getId(), 0.0);
    }

    /** Monto exacto en pesos, para comparar sin errores de decimales (el monto va en miles de COP). */
    private static long claveMonto(double montoMiles) { return Math.round(montoMiles * 1000.0); }

    private double bal(AccountCop a) { return a.getBalance() != null ? a.getBalance() : 0.0; }
    private double val(Double d)     { return d != null ? d : 0.0; }
}
