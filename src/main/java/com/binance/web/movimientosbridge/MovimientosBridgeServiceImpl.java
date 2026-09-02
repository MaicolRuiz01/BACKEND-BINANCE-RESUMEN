package com.binance.web.movimientosbridge;

import com.binance.web.Entity.AccountCop;
import com.binance.web.conciliacion.ConciliacionBancariaService;
import com.binance.web.service.CuentasP2PTelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Formatea los eventos del bridge imitando, campo por campo, el mismo
 * mensaje que ya arma _notificar_tx() en Movimientos/notificar.py — para que
 * a quien lo reciba por Telegram le sea indistinguible de una notificación
 * nativa del bot de saldo.
 *
 * Los eventos "conexion_exitosa"/"error_login" (primer login de una cuenta
 * recién activada en Cuentas P2P — ver sesiones.py→MonitorThread) además
 * actualizan el estado de conciliación de la cuenta (AccountCop), reusando
 * el mismo método seguro que ya usa conciliacion_bancaria.py — ver
 * ConciliacionBancariaService.registrarResultadoCuenta: nunca bloquea la
 * cuenta automáticamente, solo deja el aviso "no disponible" para revisión manual.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovimientosBridgeServiceImpl implements MovimientosBridgeService {

    private final CuentasP2PTelegramService cuentasP2PTelegramService;
    private final ConciliacionBancariaService conciliacionBancariaService;

    // Lista fija de chats de confianza (ver nota en application-dev.properties:
    // por ahora es una lista manual, misma idea que FULL_NOTIF_IDS en
    // Movimientos/config.py; más adelante se puede mover a una tabla propia).
    @Value("${app.cuentasp2p.chats-confiables:}")
    private String chatsConfiablesRaw;

    @Override
    public void procesarEvento(MovimientoEventoDto evento) {
        if (evento == null || evento.getEvento() == null) {
            log.warn("[CuentasP2P Bridge] Evento vacío o sin tipo — ignorado.");
            return;
        }

        // "conexion_exitosa"/"error_login" además actualizan el estado de
        // conciliación de la cuenta — independiente de si hay o no chats de
        // Telegram configurados abajo, para que esto nunca se pierda.
        if ("conexion_exitosa".equals(evento.getEvento())) {
            conciliacionBancariaService.registrarResultadoCuenta(
                    evento.getCuenta(), true, null, null);
        } else if ("error_login".equals(evento.getEvento())) {
            conciliacionBancariaService.registrarResultadoCuenta(
                    evento.getCuenta(), false, null, evento.getMotivo());
        }

        String texto = switch (evento.getEvento()) {
            case "movimiento" -> formatearMovimiento(evento);
            case "cambio_saldo" -> formatearCambioSaldo(evento);
            case "conexion_exitosa" -> formatearConexionExitosa(evento);
            case "error_login" -> formatearErrorLogin(evento);
            default -> null;
        };

        if (texto == null) {
            log.warn("[CuentasP2P Bridge] Tipo de evento desconocido: {}", evento.getEvento());
            return;
        }

        // A pedido de Milton (31/08/2026): Cuentas P2P debe respetar la MISMA
        // regla completo/resumido que ya usa notificar.py → _notificar_tx en
        // Movimientos (FULL_NOTIF_IDS reciben completo, el resto resumido).
        // Solo "movimiento" tiene versión resumida real (igual que en Python,
        // donde cambio_saldo/conexion_exitosa/error_login siempre van completos).
        String textoCorto = "movimiento".equals(evento.getEvento())
                ? formatearMovimientoCorto(evento)
                : texto;

        // Filtro (agosto 2026, a pedido de Milton): el bot de Movimientos puede
        // monitorear cuentas de forma manual (bot saldo en Telegram) SIN que
        // estén seleccionadas en "Cuentas P2P" — eso debe seguir funcionando
        // exactamente igual que siempre, pero SIN reflejarse en el bot de
        // Telegram "Cuentas P2P". Solo las cuentas con activaParaP2P=true
        // deben aparecer ahí. Si más tarde esa misma cuenta se selecciona en
        // P2P, sus eventos empiezan a pasar este filtro sin ningún cambio
        // adicional — se evalúa en cada evento, no al momento de activarla.
        Optional<AccountCop> cuentaCop = conciliacionBancariaService.buscarCuentaBancolombiaPorNombre(evento.getCuenta());
        boolean activaEnP2P = cuentaCop.map(AccountCop::getActivaParaP2P).map(Boolean.TRUE::equals).orElse(false);
        if (!activaEnP2P) {
            log.info("[CuentasP2P Bridge] '{}' no está activa en P2P — evento '{}' no se reenvía a Cuentas P2P "
                            + "(el bot de Movimientos la sigue monitoreando normal, esto solo filtra Telegram).",
                    evento.getCuenta(), evento.getEvento());
            return;
        }

        // A pedido de Milton (31/08/2026): Cuentas P2P debe notificar a los
        // MISMOS chats que ya usa el sistema de Movimientos (chats_id.json en
        // Python), no a una lista fija aparte. Si el evento trae chats_confiables
        // (lo manda pochonance_bridge.py, leído fresco de chats_id.json en cada
        // envío) se usa esa; si no viene (p.ej. una prueba manual sin ese campo),
        // se cae de vuelta a la lista fija de application-prod.properties.
        List<String> chats = (evento.getChatsConfiables() != null && !evento.getChatsConfiables().isBlank())
                ? parsearChats(evento.getChatsConfiables())
                : chatsConfiables();
        if (chats.isEmpty()) {
            log.warn("[CuentasP2P Bridge] No hay chats confiables (ni en el evento ni en app.cuentasp2p.chats-confiables) — evento de '{}' no se le manda a nadie.",
                    evento.getCuenta());
            return;
        }

        // Si chatsFull no viene (p.ej. prueba manual sin ese campo), se trata a
        // TODOS los chats como si recibieran el mensaje completo — mismo
        // comportamiento que ya teníamos antes de este cambio.
        List<String> chatsFull = (evento.getChatsFull() != null && !evento.getChatsFull().isBlank())
                ? parsearChats(evento.getChatsFull())
                : null;

        for (String chatId : chats) {
            boolean recibeCompleto = (chatsFull == null) || chatsFull.contains(chatId);
            cuentasP2PTelegramService.sendMessage(chatId, recibeCompleto ? texto : textoCorto);
        }
        log.info("[CuentasP2P Bridge] Evento '{}' de '{}' enviado a {} chat(s).",
                evento.getEvento(), evento.getCuenta(), chats.size());
    }

    // ── Formateo — mismo layout que _notificar_tx() (mensaje "completo") ──
    private String formatearMovimiento(MovimientoEventoDto e) {
        boolean esSalida = "salida".equalsIgnoreCase(e.getTipo());
        String emoji = esSalida ? "🔴" : "🟢";
        String accion = esSalida ? "Salió" : "Entró";
        String flechaAccion = esSalida ? "📤" : "📥";
        String monto = formatearMonto(e.getMonto());

        StringBuilder sb = new StringBuilder();
        sb.append(emoji).append(" *").append(nvl(e.getCuenta())).append("*\n");
        sb.append("💰 Saldo actual:   `").append(nvl(e.getSaldoActual())).append("`\n");
        sb.append("📊 Saldo anterior: `").append(nvl(e.getSaldoAnterior())).append("`\n");
        sb.append(flechaAccion).append(" ").append(accion).append(": `").append(monto).append("`\n");
        sb.append("📝 ").append(e.getDescripcion() == null || e.getDescripcion().isBlank() ? "—" : e.getDescripcion()).append("\n");
        sb.append("📅 ").append(nvl(e.getFechaTransaccion()));
        if (e.getReferencia() != null && !e.getReferencia().isBlank()) {
            sb.append("\n🔖 ").append(e.getReferencia());
        }
        return sb.toString();
    }

    // ── Formateo — versión resumida, mismo layout que msg_corto en
    // notificar.py → _notificar_tx ("Mensaje compacto (operadores externos)").
    // Usa descripcionCorta, que ya viene calculada desde Python
    // (NotificarMixin._desc_corta) — no se reimplementa esa lógica acá para
    // no tener el mismo criterio de acortado en dos lenguajes distintos.
    private String formatearMovimientoCorto(MovimientoEventoDto e) {
        boolean esSalida = "salida".equalsIgnoreCase(e.getTipo());
        String emoji = esSalida ? "🔴" : "🟢";
        String accion = esSalida ? "Salió" : "Entró";
        String flechaAccion = esSalida ? "📤" : "📥";
        String monto = formatearMonto(e.getMonto());
        String descBreve = (e.getDescripcionCorta() == null || e.getDescripcionCorta().isBlank())
                ? "—" : e.getDescripcionCorta();

        return emoji + " *" + nvl(e.getCuenta()) + "*\n"
                + flechaAccion + " " + accion + ": `" + monto + "`\n"
                + "💳 " + descBreve;
    }

    // ── Formateo — evento sin transacción identificada (solo delta de saldo) ──
    private String formatearCambioSaldo(MovimientoEventoDto e) {
        boolean esSalida = "salida".equalsIgnoreCase(e.getTipo());
        String accion = esSalida ? "Salió" : "Entró";
        String flechaAccion = esSalida ? "📤" : "📥";
        String monto = formatearMonto(e.getDelta());

        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ *").append(nvl(e.getCuenta())).append("* — cambio de saldo\n");
        sb.append("💰 `").append(nvl(e.getSaldoAnterior())).append("` → `").append(nvl(e.getSaldoActual())).append("`\n");
        sb.append(flechaAccion).append(" ").append(accion).append(": `").append(monto).append("`");
        return sb.toString();
    }

    // ── Formateo — primer login exitoso de una cuenta recién activada ──
    // A PROPÓSITO (agosto 2026, a pedido de Milton): mismo formato EXACTO que
    // ya usa el bot de Movimientos al arrancar una cuenta a mano (ver
    // iniciar.py → _esperar_y_notificar) — "✅ Nombre / 💰 Saldo / 📋 Últimos
    // movimientos: lista numerada". El bloque de movimientos viene YA armado
    // desde Python (movimientosTexto) para no duplicar el formato de
    // fecha/monto en Java — acá solo se concatena.
    private String formatearConexionExitosa(MovimientoEventoDto e) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ *").append(nvl(e.getCuenta())).append("*\n");
        sb.append("💰 Saldo: `").append(nvl(e.getSaldoActual())).append("`");
        if (e.getMovimientosTexto() != null && !e.getMovimientosTexto().isBlank()) {
            sb.append(e.getMovimientosTexto());
        } else {
            sb.append("\n_Sin movimientos recientes._");
        }
        return sb.toString();
    }

    // ── Formateo — primer login fallido de una cuenta recién activada ──
    // A PROPÓSITO: solo informa, no bloquea nada (ver ConciliacionBancariaServiceImpl
    // .registrarResultadoCuenta). "No se pudo acceder" ≠ "el banco confirmó bloqueo".
    private String formatearErrorLogin(MovimientoEventoDto e) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ *").append(nvl(e.getCuenta())).append("* — no se pudo acceder\n");
        sb.append("📝 ").append(e.getMotivo() == null || e.getMotivo().isBlank() ? "Motivo no determinado" : e.getMotivo()).append("\n");
        sb.append("👉 Revisar manualmente — no se bloqueó la cuenta automáticamente.");
        return sb.toString();
    }

    private String formatearMonto(Double valor) {
        if (valor == null) return "—";
        String formateado = String.format(Locale.US, "%,.0f", Math.abs(valor));
        return "$ " + formateado.replace(",", ".");
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private List<String> chatsConfiables() {
        return parsearChats(chatsConfiablesRaw);
    }

    private List<String> parsearChats(String raw) {
        List<String> resultado = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return resultado;
        }
        for (String parte : raw.split(",")) {
            String limpio = parte.trim();
            if (!limpio.isEmpty()) {
                resultado.add(limpio);
            }
        }
        return resultado;
    }
}
