package com.binance.web.movimientosbridge;

import java.text.Normalizer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Cuentas que se pidió abrir en Movimientos y de las que TODAVÍA no llegó la confirmación.
 *
 * Una cuenta entra acá cuando pasa a activa en P2P (o se reencola desde el heartbeat) y sale
 * cuando Movimientos manda el evento "conexion_exitosa" (login hecho y saldo leído: la prueba
 * real de que la abrió) o cuando se desactiva. La asignación automática de ventas no usa las
 * cuentas pendientes: así una venta no queda asignada a una cuenta que nadie está vigilando.
 *
 * Por qué NO sirve el heartbeat para esto: Movimientos lista una cuenta en el heartbeat apenas
 * lanza su hilo, antes de loguearse.
 *
 * Es memoria del proceso a propósito (sin tabla): si el backend se reinicia (deploy en
 * Railway) el set queda vacío y todas las cuentas se consideran listas, que es el
 * comportamiento de siempre — preferible a dejar el Auto trabado esperando un evento que
 * Movimientos ya mandó antes del reinicio.
 */
@Slf4j
@Component
public class MovimientosCuentasPendientes {

    private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}");

    private final Set<String> pendientes = ConcurrentHashMap.newKeySet();

    /** Mismo criterio de normalización que usa el heartbeat (minúsculas, sin tildes ni espacios sobrantes). */
    static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = DIACRITICOS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return sinTildes.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /** Se pidió abrir la cuenta en Movimientos: queda sin asignar ventas hasta que confirme. */
    public void marcarPendiente(String nombreCuenta) {
        String k = normalizar(nombreCuenta);
        if (k.isEmpty()) return;
        if (pendientes.add(k)) {
            log.info("[CuentasPendientes] '{}' pendiente de confirmación de Movimientos.", nombreCuenta);
        }
    }

    /** Movimientos confirmó la conexión de la cuenta (evento conexion_exitosa). */
    public void confirmar(String nombreCuenta) {
        String k = normalizar(nombreCuenta);
        if (k.isEmpty()) return;
        if (pendientes.remove(k)) {
            log.info("[CuentasPendientes] '{}' confirmada por Movimientos — ya puede recibir ventas.", nombreCuenta);
        }
    }

    /** La cuenta dejó de estar activa: ya no hay nada que esperar. */
    public void descartar(String nombreCuenta) {
        pendientes.remove(normalizar(nombreCuenta));
    }

    public boolean estaPendiente(String nombreCuenta) {
        return pendientes.contains(normalizar(nombreCuenta));
    }
}
