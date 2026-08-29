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

        List<String> chats = chatsConfiables();
        if (chats.isEmpty()) {
            log.warn("[CuentasP2P Bridge] No hay chats confiables configurados (app.cuentasp2p.chats-confiables) — evento de '{}' no se le manda a nadie.",
                    evento.getCuenta());
            return;
        }

        for (String chatId : chats) {
            cuentasP2PTelegramService.sendMessage(chatId, texto);
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
    private String formatearConexionExitosa(MovimientoEventoDto e) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ *").append(nvl(e.getCuenta())).append("* — activada y verificada\n");
        sb.append("💰 Saldo: `").append(nvl(e.getSaldoActual())).append("`\n");
        int cant = e.getCantidadMovimientos() != null ? e.getCantidadMovimientos() : 0;
        sb.append("📋 ").append(cant).append(" movimiento(s) recientes leídos.");
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
        List<String> resultado = new ArrayList<>();
        if (chatsConfiablesRaw == null || chatsConfiablesRaw.isBlank()) {
            return resultado;
        }
        for (String parte : chatsConfiablesRaw.split(",")) {
            String limpio = parte.trim();
            if (!limpio.isEmpty()) {
                resultado.add(limpio);
            }
        }
        return resultado;
    }
}
