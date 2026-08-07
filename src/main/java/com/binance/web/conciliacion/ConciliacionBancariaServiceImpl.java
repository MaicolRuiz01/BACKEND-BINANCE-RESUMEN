package com.binance.web.conciliacion;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Repository.AccountCopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ConciliacionBancariaServiceImpl implements ConciliacionBancariaService {

    private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}");

    /** Fila única de ConciliacionBotChat — solo hay UN bot/chat de conciliación. */
    private static final Integer CHAT_ID_FILA = 1;

    private final AccountCopRepository accountCopRepository;
    private final ConciliacionBotChatRepository conciliacionBotChatRepository;
    private final ConciliacionBotTelegramClient conciliacionBotTelegramClient;
    private final ConciliacionSolicitudRepository conciliacionSolicitudRepository;

    public ConciliacionBancariaServiceImpl(AccountCopRepository accountCopRepository,
            ConciliacionBotChatRepository conciliacionBotChatRepository,
            ConciliacionBotTelegramClient conciliacionBotTelegramClient,
            ConciliacionSolicitudRepository conciliacionSolicitudRepository) {
        this.accountCopRepository = accountCopRepository;
        this.conciliacionBotChatRepository = conciliacionBotChatRepository;
        this.conciliacionBotTelegramClient = conciliacionBotTelegramClient;
        this.conciliacionSolicitudRepository = conciliacionSolicitudRepository;
    }

    /** Minúsculas, sin tildes, espacios colapsados — mismo criterio que usa el
     *  bot (_normalizar_nombre en conciliacion_bancaria.py) para que "Víctor"
     *  empareje con "Victor" sin importar el acento. */
    private static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = DIACRITICOS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        return sinTildes.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    @Override
    @Transactional
    public ConciliacionResponseDto procesarResultado(ConciliacionResultadoDto request) {
        ConciliacionResponseDto response = new ConciliacionResponseDto();
        if (request == null || request.getResultados() == null || request.getResultados().isEmpty()) {
            return response;
        }

        List<AccountCop> cuentasBancolombia = accountCopRepository.findByBankType(BankType.BANCOLOMBIA);

        // Agrupa por nombre normalizado — si dos cuentas normalizan igual, el
        // nombre queda AMBIGUO y se reporta como "no encontrado" en vez de
        // arriesgarse a actualizar la cuenta equivocada.
        Map<String, List<AccountCop>> porNombre = new HashMap<>();
        for (AccountCop c : cuentasBancolombia) {
            porNombre.computeIfAbsent(normalizar(c.getName()), k -> new ArrayList<>()).add(c);
        }

        for (ConciliacionResultadoDto.Item item : request.getResultados()) {
            String nombreOriginal = item.getCuenta() != null ? item.getCuenta() : "(sin nombre)";
            List<AccountCop> candidatos = porNombre.get(normalizar(nombreOriginal));

            if (candidatos == null || candidatos.size() != 1) {
                response.getNoEncontrados().add(nombreOriginal);
                continue;
            }

            AccountCop cuenta = candidatos.get(0);
            boolean disponible = Boolean.TRUE.equals(item.getDisponible());

            cuenta.setUltimaConciliacion(LocalDateTime.now());
            cuenta.setDisponibleBanco(item.getDisponible());

            if (disponible) {
                cuenta.setUltimoErrorConciliacion(null);
                Double saldoReal = item.getSaldoRealBanco();
                Double saldoPochonance = cuenta.getBalance();
                cuenta.setUltimoDesfaseBanco(
                        (saldoReal != null && saldoPochonance != null)
                                ? Math.round((saldoReal - saldoPochonance) * 100.0) / 100.0
                                : null);

                // Auto-desbloqueo: si el bot había bloqueado esta cuenta antes (por no
                // poder leerla) y ahora sí la lee bien, se desbloquea sola. Si está
                // bloqueada pero NO fue el bot quien la bloqueó (bloqueo manual desde
                // Saldos), no se toca — un humano la desbloquea cuando quiera.
                if (Boolean.TRUE.equals(cuenta.getBloqueada()) && Boolean.TRUE.equals(cuenta.getBloqueadaPorBot())) {
                    cuenta.setBloqueada(false);
                    cuenta.setBloqueadaPorBot(false);
                }
            } else {
                cuenta.setUltimoDesfaseBanco(null);
                cuenta.setUltimoErrorConciliacion(item.getError());

                // El bot no pudo leer la cuenta (posible bloqueo u otra falla del banco)
                // -> se bloquea automáticamente en Pochonance, igual que el botón manual
                // de "Bloquear cuenta" en Saldos (sale de P2P de inmediato). Se marca
                // bloqueadaPorBot=true para poder distinguirlo de un bloqueo manual y
                // permitir el auto-desbloqueo más adelante.
                cuenta.setBloqueada(true);
                cuenta.setBloqueadaPorBot(true);
                // Mismo efecto que el botón manual "Bloquear cuenta": sale de P2P de inmediato.
                cuenta.setActivaParaP2P(false);
            }

            accountCopRepository.save(cuenta);
            response.getActualizados().add(nombreOriginal);
        }

        return response;
    }

    @Override
    @Transactional
    public void registrarChat(Long chatId) {
        if (chatId == null) return;
        ConciliacionBotChat fila = conciliacionBotChatRepository.findById(CHAT_ID_FILA)
                .orElseGet(ConciliacionBotChat::new);
        fila.setId(CHAT_ID_FILA);
        fila.setChatId(chatId);
        fila.setRegistradoEn(LocalDateTime.now());
        conciliacionBotChatRepository.save(fila);
    }

    @Override
    @Transactional
    public void solicitarConciliacion(AccountCop cuenta) {
        if (cuenta == null || cuenta.getName() == null || cuenta.getName().isBlank()) return;

        // Este es el mecanismo REAL que el bot usa para enterarse: encola una
        // solicitud pendiente que el bot consume vía polling
        // (GET /conciliacion/pendiente, ver obtenerYConsumirPendiente). Un
        // bot de Telegram nunca recibe, vía getUpdates, los mensajes que él
        // mismo mandó con sendMessage — así que "avisarle" solo por Telegram
        // nunca iba a funcionar.
        ConciliacionSolicitud solicitud = new ConciliacionSolicitud();
        solicitud.setCuenta(cuenta.getName());
        solicitud.setCreadaEn(LocalDateTime.now());
        solicitud.setConsumida(false);
        conciliacionSolicitudRepository.save(solicitud);

        // Aviso por Telegram: puramente informativo para un humano (Milton),
        // el bot ya NO depende de este mensaje para reaccionar.
        Long chatId = conciliacionBotChatRepository.findById(CHAT_ID_FILA)
                .map(ConciliacionBotChat::getChatId)
                .orElse(null);
        String texto = "🔔 Pochonance activó \"" + cuenta.getName()
                + "\" en P2P — el bot la revisará en su próximo ciclo.";
        conciliacionBotTelegramClient.enviarMensaje(chatId, texto);
    }

    @Override
    @Transactional
    public Optional<String> obtenerYConsumirPendiente() {
        return conciliacionSolicitudRepository.findFirstByConsumidaFalseOrderByCreadaEnAsc()
                .map(solicitud -> {
                    solicitud.setConsumida(true);
                    conciliacionSolicitudRepository.save(solicitud);
                    return solicitud.getCuenta();
                });
    }
}
