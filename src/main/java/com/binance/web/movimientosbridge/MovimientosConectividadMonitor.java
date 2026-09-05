package com.binance.web.movimientosbridge;

import com.binance.web.BinanceAPI.P2PActiveOrderService;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.dto.ActiveP2POrderDto;
import com.binance.web.service.CuentasP2PTelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Detecta dos escenarios de "silencio" de Movimientos que el heartbeat de
 * MovimientosHeartbeatServiceImpl, por sí solo, no cubre (ese servicio
 * reacciona CUANDO llega un heartbeat, nunca cuando DEJA de llegar):
 *
 * 1) CAÍDA TOTAL — Movimientos deja de mandar heartbeats por completo (se
 *    cerró la terminal, se cayó el internet de esa máquina, etc.).
 * 2) CAÍDA PUNTUAL DE UNA CUENTA — el resto sigue reportándose con
 *    normalidad, pero una cuenta en particular deja de aparecer en la
 *    lista de "cuentas activas" — probable bloqueo/crash de esa sesión
 *    específica, sin que el resto del sistema se entere.
 *
 * Caso real que motivó esto (04-05/09/2026): si Movimientos se cae justo
 * mientras hay una venta P2P en curso, Milton necesita enterarse en
 * segundos — por eso el heartbeat de pochonance_activador.py bajó de 120s
 * a 10s, y acá el umbral de alerta es de 30s (3 ciclos fallidos seguidos,
 * para no disparar por un solo heartbeat perdido al azar bajo carga).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovimientosConectividadMonitor {

    private static final long UMBRAL_SEGUNDOS = 30;
    private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}");

    private final AccountCopRepository accountCopRepository;
    private final CuentasP2PTelegramService cuentasP2PTelegramService;
    private final P2PActiveOrderService p2pActiveOrderService;

    @Value("${app.cuentasp2p.chats-confiables:}")
    private String chatsConfiablesRaw;

    private volatile Instant ultimoHeartbeatGlobal;
    private volatile boolean alertaCaidaGlobalEnviada = false;

    private final ConcurrentHashMap<String, Instant> ultimaVezActivaPorCuenta = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> nombreOriginalPorCuenta = new ConcurrentHashMap<>();
    private final Set<String> alertaCaidaCuentaEnviada = ConcurrentHashMap.newKeySet();

    private static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = DIACRITICOS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return sinTildes.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /** Llamado por MovimientosHeartbeatServiceImpl.reconciliar() en cada heartbeat recibido. */
    public void registrarHeartbeat(List<String> cuentasActivasReportadas) {
        Instant ahora = Instant.now();
        boolean veniamosDeCaidaGlobal = alertaCaidaGlobalEnviada;
        ultimoHeartbeatGlobal = ahora;

        if (veniamosDeCaidaGlobal) {
            alertaCaidaGlobalEnviada = false;
            enviarAlerta("🟢 *Conexión con Movimientos restablecida.*");
            log.info("[Conectividad] Heartbeat recibido de nuevo tras una caída — conexión restablecida.");
        }

        if (cuentasActivasReportadas == null) return;
        for (String nombre : cuentasActivasReportadas) {
            String key = normalizar(nombre);
            if (key.isEmpty()) continue;
            ultimaVezActivaPorCuenta.put(key, ahora);
            nombreOriginalPorCuenta.put(key, nombre);
            if (alertaCaidaCuentaEnviada.remove(key)) {
                enviarAlerta("🟢 *" + nombre + "* volvió a reportarse activa — conexión de esa cuenta restablecida.");
                log.info("[Conectividad] '{}' volvió a reportarse tras una caída puntual.", nombre);
            }
        }
    }

    @Scheduled(fixedRate = 10_000)
    public void verificarConectividad() {
        try {
            Instant ahora = Instant.now();
            Instant ultimo = ultimoHeartbeatGlobal;

            // Nunca hemos recibido un heartbeat (recién desplegado) — nada que comparar todavía.
            if (ultimo == null) return;

            long segundosSinHeartbeat = Duration.between(ultimo, ahora).getSeconds();

            if (segundosSinHeartbeat >= UMBRAL_SEGUNDOS) {
                if (!alertaCaidaGlobalEnviada) {
                    alertaCaidaGlobalEnviada = true;
                    String detalle = detalleOrdenesEnCursoTodasLasCuentas();
                    enviarAlerta("🔴 *Se perdió la conexión con Movimientos* — no se reciben heartbeats hace más de "
                            + UMBRAL_SEGUNDOS + " segundos. Si hay ventas P2P en curso, revisa manualmente."
                            + detalle);
                    log.warn("[Conectividad] Sin heartbeat hace {}s — alerta de caída total enviada.", segundosSinHeartbeat);
                }
                return; // Si ya sabemos que TODO está caído, no tiene sentido evaluar cuenta por cuenta.
            }

            // Conexión global sana — evaluar si alguna cuenta puntual dejó de reportarse
            // (posible bloqueo/crash de esa sesión específica, no un problema general).
            List<AccountCop> activas = accountCopRepository.findByActivaParaP2PTrue();
            for (AccountCop cuenta : activas) {
                String key = normalizar(cuenta.getName());
                Instant vistaPorUltimaVez = ultimaVezActivaPorCuenta.get(key);
                if (vistaPorUltimaVez == null) continue; // nunca la hemos visto reportada — puede que recién se esté activando.
                long segundosSinReportarse = Duration.between(vistaPorUltimaVez, ahora).getSeconds();
                if (segundosSinReportarse >= UMBRAL_SEGUNDOS && alertaCaidaCuentaEnviada.add(key)) {
                    String nombre = nombreOriginalPorCuenta.getOrDefault(key, cuenta.getName());
                    String detalle = detalleOrdenesEnCurso(ordenesEnCursoDeCuenta(cuenta.getId()));
                    enviarAlerta("🟡 *" + nombre + "* dejó de reportarse activa hace más de " + UMBRAL_SEGUNDOS
                            + " segundos, aunque el resto de cuentas siguen bien — posible bloqueo o caída de esa sesión puntual."
                            + detalle);
                    log.warn("[Conectividad] '{}' dejó de reportarse — alerta de caída puntual enviada.", nombre);
                }
            }
        } catch (Exception e) {
            // Best-effort a propósito: un fallo acá no debe tumbar el scheduler.
            log.error("[Conectividad] Error verificando conectividad — se ignora, se reintenta en el próximo ciclo.", e);
        }
    }

    private void enviarAlerta(String texto) {
        List<String> chats = parsearChats(chatsConfiablesRaw);
        if (chats.isEmpty()) {
            log.warn("[Conectividad] No hay chats confiables configurados — alerta no se pudo enviar: {}", texto);
            return;
        }
        for (String chatId : chats) {
            cuentasP2PTelegramService.sendMessage(chatId, texto);
        }
    }

    private List<String> parsearChats(String raw) {
        List<String> resultado = new ArrayList<>();
        if (raw == null || raw.isBlank()) return resultado;
        for (String parte : raw.split(",")) {
            String limpio = parte.trim();
            if (!limpio.isEmpty()) resultado.add(limpio);
        }
        return resultado;
    }

    /**
     * Ventas P2P en curso (aún no confirmadas/"RECIBIDO") pre-asignadas a una
     * cuenta puntual — se usan para que la alerta de caída diga exactamente
     * qué se quedó pendiente, en vez de solo avisar que la cuenta se cayó.
     * Best-effort: si la consulta a Binance falla acá, no debe tumbar la
     * alerta de conectividad en sí (esa es la parte importante).
     */
    private List<ActiveP2POrderDto> ordenesEnCursoDeCuenta(Integer cuentaCopId) {
        if (cuentaCopId == null) return List.of();
        try {
            List<ActiveP2POrderDto> todas = p2pActiveOrderService.getAllActiveOrders();
            List<ActiveP2POrderDto> resultado = new ArrayList<>();
            for (ActiveP2POrderDto orden : todas) {
                if (cuentaCopId.equals(orden.getPreAsignadoCopId())
                        && !"RECIBIDO".equalsIgnoreCase(orden.getEstadoManual())) {
                    resultado.add(orden);
                }
            }
            return resultado;
        } catch (Exception e) {
            log.error("[Conectividad] No se pudo consultar órdenes P2P activas para detallar la alerta — "
                    + "se envía la alerta sin ese detalle.", e);
            return List.of();
        }
    }

    /** Arma el bloque de texto "Ventas en curso sin confirmar" para UNA cuenta. */
    private String detalleOrdenesEnCurso(List<ActiveP2POrderDto> ordenes) {
        if (ordenes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n⚠️ Ventas en curso sin confirmar:");
        for (ActiveP2POrderDto orden : ordenes) {
            String comprador = (orden.getCounterPartNickName() == null || orden.getCounterPartNickName().isBlank())
                    ? "(sin nickname)" : orden.getCounterPartNickName();
            String monto = orden.getPesosCop() != null
                    ? String.format(Locale.forLanguageTag("es-CO"), "%,.0fK COP", orden.getPesosCop())
                    : "monto desconocido";
            sb.append("\n• ").append(comprador).append(" — ").append(monto)
                    .append(" — Orden #").append(orden.getOrderNumber());
        }
        return sb.toString();
    }

    /**
     * Igual que detalleOrdenesEnCurso, pero para el caso de caída TOTAL: se
     * recorren todas las cuentas activas en P2P y se agrupa el detalle de
     * cada una que tenga algo pendiente, para no obligar a revisar cuenta
     * por cuenta manualmente en el peor escenario (todo caído a la vez).
     */
    private String detalleOrdenesEnCursoTodasLasCuentas() {
        try {
            List<AccountCop> activas = accountCopRepository.findByActivaParaP2PTrue();
            StringBuilder sb = new StringBuilder();
            for (AccountCop cuenta : activas) {
                List<ActiveP2POrderDto> ordenes = ordenesEnCursoDeCuenta(cuenta.getId());
                if (ordenes.isEmpty()) continue;
                sb.append("\n\n🟡 *").append(cuenta.getName()).append("*:");
                for (ActiveP2POrderDto orden : ordenes) {
                    String comprador = (orden.getCounterPartNickName() == null || orden.getCounterPartNickName().isBlank())
                            ? "(sin nickname)" : orden.getCounterPartNickName();
                    String monto = orden.getPesosCop() != null
                            ? String.format(Locale.forLanguageTag("es-CO"), "%,.0fK COP", orden.getPesosCop())
                            : "monto desconocido";
                    sb.append("\n• ").append(comprador).append(" — ").append(monto)
                            .append(" — Orden #").append(orden.getOrderNumber());
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[Conectividad] No se pudo armar el detalle de órdenes en curso para la caída total.", e);
            return "";
        }
    }
}
