package com.binance.web.conciliacion;

import com.binance.web.Entity.AccountCop;

import java.util.Optional;

public interface ConciliacionBancariaService {

    /**
     * Procesa el lote de resultados que manda el bot de conciliación bancaria:
     * empareja cada fila por nombre contra las cuentas COP de Bancolombia, y
     * guarda disponibilidad + desfase (calculado con el saldo EN VIVO de
     * Pochonance, no el que mandó el bot) + error si aplica.
     */
    ConciliacionResponseDto procesarResultado(ConciliacionResultadoDto request);

    /**
     * Guarda/actualiza el chat_id de Telegram del bot de conciliación (ver
     * ConciliacionBotChat). Idempotente — si ya había uno registrado, lo
     * reemplaza (por si el bot corre ahora desde otro computador/chat).
     */
    void registrarChat(Long chatId);

    /**
     * Le avisa al bot de conciliación, por Telegram, que revise esta cuenta
     * de Bancolombia AHORA MISMO — se llama cuando se activa una cuenta en
     * "Cuentas P2P" (ver AccountCopController.toggleActivaParaP2P), en vez de
     * esperar a la próxima corrida manual del bot.
     *
     * Best-effort: si el bot todavía no registró ningún chat, o el envío por
     * Telegram falla, no lanza excepción — solo se loguea, para no romper el
     * toggle de P2P por un problema ajeno a él.
     *
     * Además de mandar el aviso por Telegram (informativo, para un humano),
     * encola una {@link ConciliacionSolicitud} pendiente — es ESE mecanismo,
     * y no Telegram, el que el bot realmente usa para enterarse (ver
     * {@link #obtenerYConsumirPendiente()}).
     */
    void solicitarConciliacion(AccountCop cuenta);

    /**
     * Devuelve el nombre de la cuenta con la solicitud de conciliación
     * pendiente más antigua, y la marca como consumida — llamado por el bot
     * de conciliación vía GET /conciliacion/pendiente en cada ciclo de
     * polling. Vacío si no hay ninguna pendiente.
     */
    Optional<String> obtenerYConsumirPendiente();

    /**
     * Registra el resultado de UN intento de acceso a una cuenta Bancolombia,
     * venga de donde venga: del lote que manda conciliacion_bancaria.py (ver
     * procesarResultado) o de un evento en vivo del bot de Movimientos (ver
     * MovimientosBridgeService, eventos "conexion_exitosa"/"error_login").
     * Único punto de escritura para disponibleBanco/ultimoErrorConciliacion —
     * así ambos canales quedan con el mismo comportamiento seguro.
     *
     * A PROPÓSITO: nunca bloquea la cuenta ni la saca de P2P por sí sola (ver
     * el comentario largo en la implementación) — eso queda siempre como una
     * decisión manual desde Saldos → "Bloquear cuenta".
     *
     * @return true si se encontró y actualizó una cuenta con ese nombre
     *         (sin ambigüedad), false si no se encontró o el nombre matcheó
     *         más de una cuenta.
     */
    boolean registrarResultadoCuenta(String nombreCuenta, boolean disponible,
            Double saldoRealBanco, String motivoError);

    /**
     * Busca una cuenta Bancolombia por nombre (normalizado, sin tildes;
     * ambiguo = no encontrado) — usado por MovimientosBridgeServiceImpl para
     * filtrar qué eventos se reenvían al bot de Telegram "Cuentas P2P": solo
     * las cuentas con activaParaP2P=true. Devuelve vacío si no se encuentra
     * o el nombre es ambiguo.
     */
    Optional<AccountCop> buscarCuentaBancolombiaPorNombre(String nombreCuenta);
}
