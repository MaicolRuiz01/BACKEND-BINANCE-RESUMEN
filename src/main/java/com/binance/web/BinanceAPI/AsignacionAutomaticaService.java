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
import com.binance.web.Entity.BankType;
import com.binance.web.activacion.CuentaP2PSyncService;
import com.binance.web.movimientosbridge.MovimientosCuentasPendientes;
import com.binance.web.service.AccountCopService;
import com.binance.web.service.RetiradorService;
import com.binance.web.dto.ActiveP2POrderDto;
import com.binance.web.util.CupoDiarioRules;
import com.binance.web.util.LimitesP2P;
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
 *     Y SOLO si la cuenta todavía no completó su cupo (disponible proyectado > 0, contando las ventas
 *     en curso ya asignadas): la tolerancia sirve para COMPLETAR el cupo, no para seguir llenando
 *     después de completado. Ej: en 9.500 llega 1.000 → queda en 10.500 (se manda); en 10.199 llega
 *     240 → no se manda. Igual para corresponsal y cajero.
 *     (el cliente permite pasarse hasta $500.000: acordado en las llamadas; antes estaba en $50.000 por
 *     error y por eso las cuentas casi llenas no recibían las ventas que las completaban).
 *  3) Si una cuenta agota su cupo (disponible ≤ −TOLERANCIA), se desactiva de P2P
 *     (avisando al bot vía {@link CuentaP2PSyncService}) y se ACTIVA la siguiente candidata con cupo.
 *  4) El cupo que cuenta depende de la HORA ({@link VentanaCupoP2P}): de 00:00 a 18:29 solo el de
 *     CORRESPONSAL y de 18:30 a 23:59 solo el de CAJERO. El otro canal se ignora. A las 18:30 el cambio
 *     es inmediato (en el siguiente ciclo): las cuentas sin espacio de cajero se cierran y se abren otras.
 *     CANAL POR CUENTA (de día): cada cuenta trabaja por CORRESPONSAL mientras le quede cupo (>= 1.000); cuando
 *     el de corresponsal se agota pasa SOLA a CAJERO si le sirve (la marca cambia, no se cierra ni se avisa a
 *     Movimientos). Al reponer el grupo se buscan primero candidatas con cupo de corresponsal y, si no quedan,
 *     de cajero: el grupo siempre son 7 y mezcla canales, así se adelanta el cajero antes de que se acabe todo
 *     el corresponsal. De noche (desde las 18:30) todas trabajan por cajero. Al día siguiente, con los cupos
 *     nuevos, vuelve solo a corresponsal.
 *     SALDO PARA CAJERO: al medir el espacio de cajero se resta del saldo lo ya pedido para retirar por
 *     CORRESPONSAL y aún sin completar (11,5M con 10M pedidos = 1,5M). Al revés NO: un retiro pendiente del mismo
 *     canal no devuelve cupo, el cupo de corresponsal sigue gastado hasta que se complete. Y una cuenta con 12M
 *     que aún no se retiró NO tiene cupo de corresponsal (cupo − saldo ya es negativo).
 *  5) SELECCIÓN DE CUENTAS: si NO hay ninguna cuenta activa en P2P, el Auto elige y activa las 7 más
 *     cercanas al límite del canal de la hora (AccountCopService.activarCincoCuentasMasCercanasAlCupo),
 *     lo que además le avisa a Movimientos para que las abra. Pasa al prenderlo y, si ya está
 *     prendido, cuando llega una venta y no queda ninguna cuenta activa. Si ya hay cuentas activas
 *     (aunque sean pocas) NO elige otras: trabaja con las que están.
 *  6) SOLO BANCOLOMBIA: es el único banco que Movimientos monitorea, así que solo esas cuentas se
 *     eligen y reciben ventas del Auto (por ahora).
 *  7) CONFIRMADAS: una cuenta recién activada no recibe ventas hasta que Movimientos confirme que la
 *     abrió (evento "conexion_exitosa", ver {@link MovimientosCuentasPendientes}).
 *  8) SIEMPRE 7 QUE RECIBAN VENTAS: en cada ciclo se cuenta cuántas cuentas activas todavía pueden recibir
 *     ventas (disponible proyectado > 0, contando lo comprometido) y, si hay menos de {@link #GRUPO_OBJETIVO},
 *     se abren candidatas hasta completar. Una cuenta que se quedó sin cupo del canal deja de contar AL
 *     INSTANTE y su reposición se abre de inmediato, aunque la vieja siga activa.
 *     TOPE DE HIERRO: la reposición nunca abre cuentas por encima de {@link LimitesP2P#MAX_CUENTAS_ACTIVAS} (8)
 *     activas EN TOTAL (cuentan también las que ya no reciben ventas pero siguen vigiladas esperando sus ventas
 *     abiertas), porque el computador de Movimientos no aguanta más sesiones. Si hay que elegir, gana el tope
 *     sobre el objetivo de 7 que reciben ventas; en cajero el rescate sigue asignando entre las activas.
 *  9) CUENTA LLENA: una cuenta se desactiva (deja de monitorearse) cuando su cupo del canal ya se cumplió con
 *     plata REAL (cupo − saldo ≤ 0) y NO le queda ninguna venta abierta. Mientras tenga ventas abiertas
 *     sigue activa y monitoreada (no recibe más), y si una se cae recupera espacio; cuando se cierran, sale.
 *     No hace falta reponerla al salir: la reposición ya se abrió al dejar de recibir. Se revisa en cada
 *     ciclo, haya o no ventas por asignar. Lo comprometido y las ventas abiertas salen de SaldosEnCursoService (la misma
 *     fuente del naranja de la pantalla): una orden recién liberada y aún sin importar sigue contando, y las
 *     pre-asignaciones huérfanas no bloquean.
 * 10) TIPO DE CUPO: las cuentas que el Auto elige o repone quedan marcadas (cupoTipoP2P) con el canal
 *     de la hora. Cuando el canal de trabajo CAMBIA (18:30, medianoche, o porque se acabó el corresponsal), las
 *     cuentas activas que siguen recibiendo ventas en el canal nuevo pasan a ese canal SIN avisar a Movimientos:
 *     solo cambia la marca, la cuenta no se cierra ni se reabre. Las que no sirven para el canal nuevo se cierran
 *     por la regla de cuenta llena. Dentro de una misma ventana no se pisa un cambio hecho a mano, y una cuenta
 *     marcada AMBOS no se toca.
 *     OJO: ese tipo decide por qué canal se dispara el retiro automático de la cuenta.
 * 11) RETIRO DE CORTE: la primera vez del día que el trabajo pasa a CAJERO por la hora (18:30), y solo en los
 *     primeros {@link #VENTANA_CORTE_MIN} minutos, se pide por CORRESPONSAL todo lo que cada cuenta activa pueda
 *     retirar por ese canal (RetiradorService.solicitarRetiroCorteCorresponsal). Se hace ANTES de cerrar cuentas,
 *     para que las que se cierran no se queden sin su retiro, y así las cuentas quedan en cero para cajero.
 *     Se apaga con p2p.retiro-corte-corresponsal.habilitado=false.
 * 12) RESCATE EN CAJERO ("una venta es una venta"): con el canal de trabajo en CAJERO no hay tope práctico.
 *     Si la venta no cabe en ninguna cuenta (ni con la tolerancia), se asigna igual a la cuenta ACTIVA con MÁS
 *     espacio libre (la de menor saldo), para poder retirar pronto y dejar las demás libres para ventas chicas.
 *     Solo entre cuentas activas y ya confirmadas por Movimientos (hay que poder vigilar el depósito). La regla
 *     de no repetidas se respeta mientras haya otra cuenta posible: se prefiere una sin una venta abierta del
 *     mismo monto exacto; solo si TODAS las activas ya tienen una igual, se asigna igual a la de más espacio
 *     (una venta hay que asignarla). Por ahora SOLO en cajero; en corresponsal una venta que no cabe queda sin asignar.
 * 13) NO REPETIDAS: una cuenta que ya tiene una venta EN CURSO por el mismo monto exacto no recibe
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
    /** Minutos después de las 18:30 en que todavía se hace el retiro de corte (cubre un reinicio del backend). */
    private static final long VENTANA_CORTE_MIN = 30;
    /** Cuántas cuentas deben estar recibiendo ventas siempre (igual que AccountCopServiceImpl.CUENTAS_A_ACTIVAR_POR_JORNADA). */
    private static final int GRUPO_OBJETIVO = 7;
    /** Una cuenta está "llena" cuando su espacio real en el canal (cupo − saldo, MILES) llega a esto o menos. */
    private static final double LIMITE_LLENA = 0.0;

    @Autowired private P2PActiveOrderService activeOrderService;
    @Autowired private AccountCopRepository accountCopRepository;
    @Autowired private CuentaP2PSyncService cuentaP2PSyncService;
    @Autowired private AutoAsignacionConfigRepository configRepository;
    /**
     * Fuente ÚNICA de lo "en curso" por cuenta: la misma que pinta el naranja de la pantalla. Incluye las
     * órdenes activas Y las que acaban de salir del listado esperando su importación (ya no están entre las
     * activas pero su plata todavía no está en el saldo), y descarta las huérfanas.
     */
    @Autowired private SaldosEnCursoService saldosEnCursoService;
    /** Cuentas pedidas a Movimientos y aun sin confirmar: no reciben ventas hasta que confirme. */
    @Autowired private MovimientosCuentasPendientes cuentasPendientes;
    /** @Lazy: solo se usa para el retiro de corte de las 18:30. */
    @Autowired @Lazy private RetiradorService retiradorService;
    /** Interruptor del retiro de corte de las 18:30. */
    @org.springframework.beans.factory.annotation.Value("${p2p.retiro-corte-corresponsal.habilitado:true}")
    private boolean corteHabilitado;
    /** Monto mínimo (miles de COP) para pedir el retiro de corte de una cuenta; por debajo no se molesta a los retiradores. */
    @org.springframework.beans.factory.annotation.Value("${p2p.retiro-corte-corresponsal.minimo-miles:500}")
    private double corteMinimoMiles;
    /** Día en que ya se hizo el retiro de corte (en memoria: tras un reinicio dentro de la ventana se repite, y no duplica). */
    private volatile java.time.LocalDate fechaCorteEjecutado;

    /** @Lazy: evita un ciclo de dependencias; solo se usa para elegir las cuentas iniciales. */
    @Autowired @Lazy private AccountCopService accountCopService;
    @Autowired @Lazy private AsignacionAutomaticaService self;

    /** Retiros por CORRESPONSAL aún sin completar, por la cuenta de la que van a salir (monto en MILES). */
    @Autowired private com.binance.web.Repository.SolicitudRetiroRepository solicitudRetiroRepository;

    /**
     * Estado del ciclo en curso (el motor corre de a un ciclo por vez, ver {@link #enCurso}): el canal con el que
     * trabaja CADA cuenta y los retiros de corresponsal pendientes. Se rehacen al inicio de cada ciclo.
     */
    private volatile Map<Integer, Canal> canalesCiclo = new HashMap<>();
    private volatile Map<Integer, Double> retiroCorrPendCiclo = new HashMap<>();

    /** Último canal con el que se marcó cada cuenta (null tras un reinicio): evita pisar un cambio hecho a mano. */
    private final Map<Integer, Canal> canalAplicado = new java.util.concurrent.ConcurrentHashMap<>();

    /** Evita repetir en cada ciclo el aviso de "se alcanzó el tope de cuentas activas". */
    private volatile boolean topeAvisado;

    /** Evita repetir en cada ciclo el aviso de "no hay candidatas para reponer el grupo". */
    private volatile boolean sinCandidatasAvisado;

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

        // Canal de la hora (corresponsal de día, cajero desde 18:30). Cada cuenta puede trabajar por el otro canal si
        // el de la hora ya no le sirve (ver derivarCanal); ese canal propio se calcula una vez por ciclo.
        Canal porHora = VentanaCupoP2P.canalAhora(reloj);
        Canal canal = prepararCiclo(todas, porHora);
        List<AccountCop> cambiadas = new ArrayList<>();

        // 0) Retiro de corte de las 18:30: antes de cerrar o re-marcar nada, para que las cuentas que se cierran
        //    también queden con su retiro de corresponsal pedido.
        retiroDeCorteSiCorresponde(todas, porHora);

        // Lo que está "en curso" por cuenta (una sola lectura por ciclo, misma foto de la BD que el saldo de arriba).
        EnCurso enCursoCuentas = calcularEnCurso(ordenes);

        // 1) Mantener el grupo: sacar las ya llenas sin ventas abiertas y reponer hasta tener 7 que reciban ventas
        //    (también abre las primeras cuentas si no había ninguna activa).
        mantenerGrupo(todas, enCursoCuentas, canal, cambiadas);

        // 1b) Si el canal de trabajo cambió, las cuentas que siguen sirviendo pasan a él (solo la marca, sin avisar a Movimientos).
        aplicarCanalSiCambio(todas, enCursoCuentas, canal, cambiadas);

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

        // "Comprometido" = plata de las ventas EN CURSO ya pre-asignadas a cada cuenta (incluye las recién
        // liberadas que aún no se importaron). Todavía no bajaron el balance: hay que restarlas aparte
        // para no sobre-asignar la misma cuenta. Se copia porque dentro del ciclo se va sumando.
        Map<Integer, Double> comprometido = new HashMap<>(enCursoCuentas.comprometido());

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
                // Una venta es una venta: en cajero no hay tope práctico, va a la cuenta de cajero activa con más espacio.
                // (De día sin ninguna cuenta de cajero en el grupo, no hay rescate: queda sin asignar.)
                elegida = elegirCuentaDeRescate(todas, comprometido, montosAbiertos, monto, canal);
                if (elegida != null) {
                    log.info("[AutoAsign] La orden {} ({} miles) no cabe en ninguna cuenta de {}: se asigna de rescate a {} (espacio {} miles).",
                            o.getOrderNumber(), monto, canal, elegida.getName(), disponible(elegida, comprometido, canal));
                }
            }
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

    // ── Retiro de corte de las 18:30 ──────────────────────────────

    private void retiroDeCorteSiCorresponde(List<AccountCop> todas, Canal porHora) {
        if (!corteHabilitado || porHora != Canal.CAJERO) return;
        java.time.ZonedDateTime ahora = java.time.ZonedDateTime.now(reloj.withZone(VentanaCupoP2P.ZONA));
        if (ahora.toLocalTime().isAfter(VentanaCupoP2P.INICIO_CAJERO.plusMinutes(VENTANA_CORTE_MIN))) return;
        java.time.LocalDate hoy = ahora.toLocalDate();
        if (hoy.equals(fechaCorteEjecutado)) return;
        fechaCorteEjecutado = hoy;

        List<AccountCop> deCorte = todas.stream().filter(this::esDelGrupo).collect(Collectors.toList());
        try {
            int creadas = retiradorService.solicitarRetiroCorteCorresponsal(deCorte, corteMinimoMiles);
            log.info("[AutoAsign] Retiro de corte de las 18:30: {} solicitud(es) de corresponsal sobre {} cuenta(s) activa(s).",
                    creadas, deCorte.size());
        } catch (Exception e) {
            log.error("[AutoAsign] Falló el retiro de corte de las 18:30: {}", e.getMessage());
        }
    }

    // ── Canal de cada cuenta ──────────────────────────────────────

    /** Rehace, para este ciclo, los retiros de corresponsal pendientes y el canal con el que trabaja cada cuenta. */
    private Canal prepararCiclo(List<AccountCop> todas, Canal porHora) {
        Map<Integer, Double> pendientes = retirosCorresponsalPendientes();
        Map<Integer, Canal> canales = new HashMap<>();
        for (AccountCop a : todas) {
            if (a.getId() == null || a.getBankType() != BankType.BANCOLOMBIA) continue;
            canales.put(a.getId(), derivarCanal(a, porHora, pendientes.getOrDefault(a.getId(), 0.0)));
        }
        retiroCorrPendCiclo = pendientes;
        canalesCiclo = canales;
        return porHora;
    }

    /** Retiros por corresponsal pedidos y sin completar, por cuenta (MILES). Si falla la lectura, se asume ninguno. */
    private Map<Integer, Double> retirosCorresponsalPendientes() {
        Map<Integer, Double> m = new HashMap<>();
        try {
            for (Object[] fila : solicitudRetiroRepository.sumMontoCorresponsalPendientePorCuenta()) {
                m.put(((Number) fila[0]).intValue(), ((Number) fila[1]).doubleValue());
            }
        } catch (Exception e) {
            log.warn("[AutoAsign] No se pudieron leer los retiros pendientes por corresponsal: {}", e.getMessage());
        }
        return m;
    }

    /**
     * Canal con el que trabaja una cuenta: de noche, cajero. De día, corresponsal mientras le queden
     * {@link #SUBLIMITE_ACTIVAR} o más de cupo; si no, cajero cuando todavía le sirve (contando como ya fuera del
     * saldo lo pedido para retirar por corresponsal); y si tampoco, corresponsal (la cuenta está llena y se cierra).
     */
    private Canal derivarCanal(AccountCop a, Canal porHora, double retiroCorrPend) {
        if (porHora == Canal.CAJERO) return Canal.CAJERO;
        if (VentanaCupoP2P.cupoHoy(a, Canal.CORRESPONSAL) - bal(a) >= SUBLIMITE_ACTIVAR) return Canal.CORRESPONSAL;
        double espacioCajero = VentanaCupoP2P.cupoHoy(a, Canal.CAJERO) - (bal(a) - retiroCorrPend);
        return espacioCajero > LIMITE_LLENA ? Canal.CAJERO : Canal.CORRESPONSAL;
    }

    /** Canal con el que trabaja esta cuenta en este ciclo (si no se calculó, el de la hora). */
    private Canal canalDe(AccountCop a, Canal porHora) {
        Canal c = a.getId() != null ? canalesCiclo.get(a.getId()) : null;
        return c != null ? c : porHora;
    }

    /** Saldo para medir el espacio en un canal: en cajero se descuenta lo pedido para retirar por corresponsal. */
    private double saldoParaCanal(AccountCop a, Canal c) {
        double pend = c == Canal.CAJERO && a.getId() != null ? retiroCorrPendCiclo.getOrDefault(a.getId(), 0.0) : 0.0;
        return bal(a) - pend;
    }

    /** Espacio real (MILES) que le queda a la cuenta en su canal, sin contar ventas en curso. */
    private double espacio(AccountCop a, Canal c) {
        return VentanaCupoP2P.cupoHoy(a, c) - saldoParaCanal(a, c);
    }

    // ── Mantenimiento del grupo ───────────────────────────────────

    /** Lo que cada cuenta tiene en curso: plata comprometida y cuáles tienen alguna venta abierta. */
    private record EnCurso(Map<Integer, Double> comprometido, Set<Integer> conVentas) {}

    /**
     * Une dos miradas a lo "en curso": SaldosEnCursoService (órdenes activas + recién salidas esperando
     * importación, sin huérfanas) y la lista de órdenes activas del poll. Una orden no se cuenta dos
     * veces (se deduplica por número de orden). Así una venta recién liberada y aún sin importar sigue
     * contando como comprometida, que era el punto ciego que dejaba llenar de más la cuenta más cercana al límite.
     */
    private EnCurso calcularEnCurso(List<ActiveP2POrderDto> ordenes) {
        Map<Integer, Double> comprometido = new HashMap<>();
        Set<Integer> conVentas = new HashSet<>();
        Set<String> vistas = new HashSet<>();

        List<SaldosEnCursoService.SaldoEnCurso> deServicio = saldosEnCursoService.calcular();
        if (deServicio != null) {
            for (SaldosEnCursoService.SaldoEnCurso s : deServicio) {
                if (s.id() == null || s.detalle() == null) continue;
                for (SaldosEnCursoService.DetalleEnCurso d : s.detalle()) {
                    comprometido.merge(s.id(), d.pesos(), Double::sum);
                    conVentas.add(s.id());
                    vistas.add(d.orderNumber());
                }
            }
        }
        for (ActiveP2POrderDto o : ordenes) {
            if (o.getPreAsignadoCopId() == null || vistas.contains(o.getOrderNumber())) continue;
            comprometido.merge(o.getPreAsignadoCopId(), val(o.getPesosCop()), Double::sum);
            conVentas.add(o.getPreAsignadoCopId());
        }
        return new EnCurso(comprometido, conVentas);
    }

    /**
     * Mantiene el grupo en dos pasos:
     *  1) Desactiva (deja de monitorear) las cuentas cuyo cupo del canal ya se cumplió con plata real y que no
     *     esperan ninguna venta.
     *  2) Cuenta las activas que todavía pueden recibir ventas y, si hay menos de {@link #GRUPO_OBJETIVO}, abre
     *     candidatas hasta completar. Una cuenta sin cupo pero con ventas abiertas sigue activa y monitoreada,
     *     pero ya NO cuenta aquí: por eso su reposición se abre de inmediato y no cuando por fin se cierre.
     */
    private void mantenerGrupo(List<AccountCop> todas, EnCurso enCurso, Canal canal, List<AccountCop> cambiadas) {
        // 1) Tomar el control: si hay MÁS cuentas recibiendo que el objetivo (o más activas que el tope), se cierran
        //    las que sobran, de a una. Nunca una con ventas abiertas; entre las demás, la de menos espacio libre.
        //    Es por la memoria del computador de Movimientos: 7 es lo sano, la 8ª es solo margen.
        cerrarSobrantes(todas, enCurso, canal, cambiadas);

        // 2) Primero se pide la reposición (Movimientos tarda en abrir una cuenta)...
        // 3) ...y solo cuando la nueva ya está confirmada se cierran las llenas. Así siempre hay 7 trabajando.
        //    Se repite mientras algo cambie: al cerrar una llena queda sitio para pedir la siguiente reposición
        //    (la cuenta nueva sigue "pendiente" hasta que Movimientos la confirme, así que en la práctica el
        //    cambio avanza de a una por confirmación y nunca pasa del tope de 8).
        for (int i = 0; i <= LimitesP2P.MAX_CUENTAS_ACTIVAS; i++) {
            int antes = cambiadas.size();
            reponerGrupo(todas, enCurso, canal, cambiadas);
            cerrarLlenas(todas, enCurso, canal, cambiadas);
            if (cambiadas.size() == antes) break;
        }
    }

    /** Abre candidatas hasta tener {@link #GRUPO_OBJETIVO} cuentas que puedan recibir ventas (sin pasar del tope). */
    private void reponerGrupo(List<AccountCop> todas, EnCurso enCurso, Canal canal, List<AccountCop> cambiadas) {
        long recibiendo = todas.stream()
                .filter(this::esDelGrupo)
                .filter(a -> disponible(a, enCurso.comprometido(), canal) > 0)
                .count();
        long activas = todas.stream().filter(this::esDelGrupo).count();
        while (recibiendo < GRUPO_OBJETIVO) {
            if (activas >= LimitesP2P.MAX_CUENTAS_ACTIVAS) {
                if (!topeAvisado) {
                    log.warn("[AutoAsign] Tope de {} cuentas activas alcanzado: {} reciben ventas y {} siguen vigiladas esperando sus ventas abiertas. No se abren más.",
                            LimitesP2P.MAX_CUENTAS_ACTIVAS, recibiendo, activas - recibiendo);
                    topeAvisado = true;
                }
                return;
            }
            AccountCop siguiente = activarSiguiente(todas, canal);
            if (siguiente == null) {
                if (!sinCandidatasAvisado) {
                    log.warn("[AutoAsign] Solo {} de {} cuentas reciben ventas y no hay más candidatas con cupo de {}.",
                            recibiendo, GRUPO_OBJETIVO, canal);
                    sinCandidatasAvisado = true;
                }
                return;
            }
            cambiadas.add(siguiente);
            recibiendo++;
            activas++;
        }
        topeAvisado = false;
        sinCandidatasAvisado = false;
    }

    /**
     * Cierra las cuentas llenas (cupo del canal cumplido con plata real) que no esperan ninguna venta, pero NO antes
     * de que su reemplazo esté listo: mientras haya una reposición pidiéndose a Movimientos y todavía no haya
     * {@link #GRUPO_OBJETIVO} cuentas confirmadas recibiendo, la llena se queda abierta (es la 8ª, el margen).
     * Si no hay reposición en camino (no quedan candidatas), se cierra de inmediato como siempre.
     */
    private void cerrarLlenas(List<AccountCop> todas, EnCurso enCurso, Canal canal, List<AccountCop> cambiadas) {
        List<AccountCop> recibiendo = todas.stream()
                .filter(this::esDelGrupo)
                .filter(a -> disponible(a, enCurso.comprometido(), canal) > 0)
                .collect(Collectors.toList());
        long confirmadas = recibiendo.stream().filter(a -> !cuentasPendientes.estaPendiente(a.getName())).count();
        boolean reposicionEnCamino = recibiendo.stream().anyMatch(a -> cuentasPendientes.estaPendiente(a.getName()));
        boolean esperar = reposicionEnCamino && confirmadas < GRUPO_OBJETIVO;

        for (AccountCop a : new ArrayList<>(todas)) {
            if (!esDelGrupo(a)) continue;
            if (espacio(a, canalDe(a, canal)) > LIMITE_LLENA) continue; // todavía tiene espacio (en su canal)
            if (enCurso.conVentas().contains(a.getId())) continue; // espera plata: se queda hasta que se cierre
            if (esperar) {
                log.info("[AutoAsign] {} llegó al límite de {} pero su reemplazo aún no está confirmado por Movimientos: sigue abierta.",
                        a.getName(), canal);
                continue;
            }

            log.info("[AutoAsign] {} llegó al límite de {} sin ventas abiertas → deja de monitorearse.", a.getName(), canal);
            desactivar(a);
            cambiadas.add(a);
        }
    }

    /**
     * Cierra las cuentas que sobran: mientras haya más de {@link #GRUPO_OBJETIVO} recibiendo ventas, o más de
     * {@link LimitesP2P#MAX_CUENTAS_ACTIVAS} activas, se desactiva una. Candidatas: solo cuentas SIN ventas abiertas
     * (una cuenta que espera plata no se toca jamás); primero las que Movimientos ya confirmó (cerrar una recién
     * pedida desperdicia el trabajo), y entre ellas la de MENOS espacio libre, que es la que antes se va a llenar.
     * Esto es lo que hace que, al prender el Auto con 8 o más cuentas abiertas a mano, el sistema tome el control.
     */
    private void cerrarSobrantes(List<AccountCop> todas, EnCurso enCurso, Canal canal, List<AccountCop> cambiadas) {
        while (true) {
            List<AccountCop> grupo = todas.stream().filter(this::esDelGrupo).collect(Collectors.toList());
            long recibiendo = grupo.stream().filter(a -> disponible(a, enCurso.comprometido(), canal) > 0).count();
            if (recibiendo <= GRUPO_OBJETIVO && grupo.size() <= LimitesP2P.MAX_CUENTAS_ACTIVAS) return;

            AccountCop sobra = grupo.stream()
                    .filter(a -> !enCurso.conVentas().contains(a.getId()))
                    .min(Comparator.<AccountCop, Boolean>comparing(a -> cuentasPendientes.estaPendiente(a.getName()))
                            .thenComparingDouble(a -> disponible(a, enCurso.comprometido(), canal))
                            .thenComparing(a -> a.getId()))
                    .orElse(null);
            if (sobra == null) {
                log.debug("[AutoAsign] Hay {} cuentas activas de más, pero todas esperan ventas abiertas: no se cierra ninguna.",
                        grupo.size());
                return;
            }
            log.info("[AutoAsign] Hay {} activas ({} reciben ventas, objetivo {}): se cierra {} (la de menos espacio, sin ventas abiertas).",
                    grupo.size(), recibiendo, GRUPO_OBJETIVO, sobra.getName());
            boolean antes = Boolean.TRUE.equals(sobra.getActivaParaP2P());
            sobra.setActivaParaP2P(false);
            cuentaP2PSyncService.sincronizar(sobra, antes);
            cambiadas.add(sobra);
        }
    }

    /**
     * Cuando el canal de trabajo cambia (o es el primer ciclo tras un reinicio), las cuentas activas que
     * SIGUEN recibiendo ventas en el canal nuevo pasan a ese canal. Solo se cambia la marca (cupoTipoP2P): no se
     * llama a CuentaP2PSyncService, así que Movimientos no se entera y la sesión de la cuenta sigue corriendo.
     * Una cuenta marcada AMBOS no se toca; una que ya no sirve para el canal se queda como está hasta que
     * salga del grupo. Dentro de una misma ventana no se vuelve a tocar (no se pisa un cambio manual).
     */
    private void aplicarCanalSiCambio(List<AccountCop> todas, EnCurso enCurso, Canal canal, List<AccountCop> cambiadas) {
        for (AccountCop a : todas) {
            if (!esDelGrupo(a)) {
                if (a.getId() != null) canalAplicado.remove(a.getId());
                continue;
            }
            Canal propio = canalDe(a, canal);
            Canal previo = canalAplicado.put(a.getId(), propio);
            if (propio == previo) continue; // su canal no cambió desde la última vez: no se pisa un cambio hecho a mano
            String actual = a.getCupoTipoP2P();
            if (!"CORRESPONSAL".equals(actual) && !"CAJERO".equals(actual)) continue; // AMBOS u otro: no se toca
            if (propio.name().equals(actual)) continue;
            if (disponible(a, enCurso.comprometido(), canal) <= 0) continue;          // no sirve para el canal nuevo
            log.info("[AutoAsign] {} pasa de {} a {} (sigue activa, sin avisar a Movimientos).", a.getName(), actual, propio);
            a.setCupoTipoP2P(propio.name());
            cambiadas.add(a);
        }
    }

    /** Cuenta Bancolombia activa en P2P y no bloqueada: la que Movimientos está (o debe estar) monitoreando. */
    private boolean esDelGrupo(AccountCop a) {
        return a.getId() != null
                && Boolean.TRUE.equals(a.getActivaParaP2P())
                && !Boolean.TRUE.equals(a.getBloqueada())
                && a.getBankType() == BankType.BANCOLOMBIA;
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
                .filter(a -> disponible(a, comprometido, canal) > 0) // cupo aún sin completar: la tolerancia solo sirve para completarlo
                .filter(a -> disponible(a, comprometido, canal) - monto >= -TOLERANCIA) // cabe (hasta 500k de exceso)
                .min(Comparator.comparingDouble(a -> disponible(a, comprometido, canal)))
                .orElse(null);
    }

    /**
     * Rescate: cuando la venta no cabe en ninguna cuenta, la cuenta ACTIVA con más espacio libre (menor saldo).
     * Se ignora el cupo. Solo cuentas de Bancolombia, no bloqueadas y ya confirmadas por Movimientos (hay que
     * poder vigilar el depósito). Primero se intenta con las que NO tienen una venta abierta del mismo monto
     * exacto; si no queda ninguna (todas tienen una igual), se asigna igual a la de más espacio.
     */
    private AccountCop elegirCuentaDeRescate(List<AccountCop> todas, Map<Integer, Double> comprometido,
                                             Map<Integer, Set<Long>> montosAbiertos, double monto, Canal canal) {
        long clave = claveMonto(monto);
        List<AccountCop> vigiladas = todas.stream()
                .filter(this::esDelGrupo)
                .filter(a -> canalDe(a, canal) == Canal.CAJERO) // el rescate solo existe en cajero
                .filter(a -> !cuentasPendientes.estaPendiente(a.getName()))
                .collect(Collectors.toList());
        Comparator<AccountCop> masEspacio = Comparator.<AccountCop>comparingDouble(a -> disponible(a, comprometido, canal))
                .thenComparing(Comparator.comparingInt((AccountCop a) -> a.getId()).reversed());

        // Primero las que NO tienen una venta abierta del mismo monto; si no queda ninguna, la de más espacio.
        return vigiladas.stream()
                .filter(a -> !montosAbiertos.getOrDefault(a.getId(), Set.of()).contains(clave))
                .max(masEspacio)
                .orElseGet(() -> vigiladas.stream().max(masEspacio).orElse(null));
    }

    /** Activa la siguiente candidata (inactiva, con cupo), la más cercana al límite — igual que la selección de las 7. */
    private AccountCop activarSiguiente(List<AccountCop> todas, Canal canal) {
        List<AccountCop> candidatas = todas.stream()
                .filter(a -> a.getId() != null)
                .filter(a -> !Boolean.TRUE.equals(a.getActivaParaP2P()))
                .filter(a -> !Boolean.TRUE.equals(a.getBloqueada()))
                .filter(a -> a.getBankType() == BankType.BANCOLOMBIA)
                .collect(Collectors.toList());
        // De día se busca primero por corresponsal; si no queda ninguna con cupo, por cajero (se adelanta el cajero).
        // De noche solo cajero.
        AccountCop next = null;
        Canal elegido = null;
        if (canal == Canal.CORRESPONSAL) {
            next = candidatas.stream()
                    .filter(a -> espacio(a, Canal.CORRESPONSAL) >= SUBLIMITE_ACTIVAR)
                    .min(Comparator.comparingDouble(a -> espacio(a, Canal.CORRESPONSAL)))
                    .orElse(null);
            elegido = Canal.CORRESPONSAL;
        }
        if (next == null) {
            next = candidatas.stream()
                    .filter(a -> espacio(a, Canal.CAJERO) >= SUBLIMITE_ACTIVAR)
                    .min(Comparator.comparingDouble(a -> espacio(a, Canal.CAJERO)))
                    .orElse(null);
            elegido = Canal.CAJERO;
        }
        if (next == null) {
            log.debug("[AutoAsign] No hay más cuentas candidatas con cupo para activar.");
            return null;
        }
        boolean antes = Boolean.TRUE.equals(next.getActivaParaP2P());
        next.setActivaParaP2P(true);
        next.setCupoTipoP2P(elegido.name());
        canalesCiclo.put(next.getId(), elegido); // en este mismo ciclo ya cuenta con su canal
        cuentaP2PSyncService.sincronizar(next, antes);
        log.info("[AutoAsign] Activada la siguiente cuenta COP: {} (por {}).", next.getName(), elegido);
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
        return espacio(acc, canalDe(acc, canal)) - comprometido.getOrDefault(acc.getId(), 0.0);
    }

    /** Monto exacto en pesos, para comparar sin errores de decimales (el monto va en miles de COP). */
    private static long claveMonto(double montoMiles) { return Math.round(montoMiles * 1000.0); }

    private double bal(AccountCop a) { return a.getBalance() != null ? a.getBalance() : 0.0; }
    private double val(Double d)     { return d != null ? d : 0.0; }
}
