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
}
