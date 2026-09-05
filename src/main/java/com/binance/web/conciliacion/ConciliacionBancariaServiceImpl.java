package com.binance.web.conciliacion;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.detencion.DetencionService;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
public class ConciliacionBancariaServiceImpl implements ConciliacionBancariaService {

    private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}");

    /** Fila única de ConciliacionBotChat — solo hay UN bot/chat de conciliación. */
    private static final Integer CHAT_ID_FILA = 1;

    private final AccountCopRepository accountCopRepository;
    private final ConciliacionBotChatRepository conciliacionBotChatRepository;
    private final ConciliacionBotTelegramClient conciliacionBotTelegramClient;
    private final ConciliacionSolicitudRepository conciliacionSolicitudRepository;
    // OJO: DetencionService directo, NO CuentaP2PSyncService — CuentaP2PSyncService
    // depende de ConciliacionBancariaService (para el lado "activar"), así que
    // inyectarlo acá también crearía una dependencia circular entre los dos
    // beans. DetencionService no depende de nada de conciliación, así que no
    // tiene ese problema.
    private final DetencionService detencionService;

    public ConciliacionBancariaServiceImpl(AccountCopRepository accountCopRepository,
            ConciliacionBotChatRepository conciliacionBotChatRepository,
            ConciliacionBotTelegramClient conciliacionBotTelegramClient,
            ConciliacionSolicitudRepository conciliacionSolicitudRepository,
            DetencionService detencionService) {
        this.accountCopRepository = accountCopRepository;
        this.conciliacionBotChatRepository = conciliacionBotChatRepository;
        this.conciliacionBotTelegramClient = conciliacionBotTelegramClient;
        this.conciliacionSolicitudRepository = conciliacionSolicitudRepository;
        this.detencionService = detencionService;
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

        for (ConciliacionResultadoDto.Item item : request.getResultados()) {
            String nombreOriginal = item.getCuenta() != null ? item.getCuenta() : "(sin nombre)";
            boolean actualizada = registrarResultadoCuenta(
                    nombreOriginal,
                    Boolean.TRUE.equals(item.getDisponible()),
                    item.getSaldoRealBanco(),
                    item.getError());
            if (actualizada) {
                response.getActualizados().add(nombreOriginal);
            } else {
                response.getNoEncontrados().add(nombreOriginal);
            }
        }

        return response;
    }

    /**
     * Busca UNA cuenta Bancolombia por nombre, con el mismo criterio robusto
     * (normalizado, sin tildes, y trata un nombre ambiguo como "no
     * encontrado" en vez de arriesgarse a devolver la cuenta equivocada) que
     * ya usaba registrarResultadoCuenta. Extraído como método público para
     * que MovimientosBridgeServiceImpl pueda preguntar "¿esta cuenta está
     * activa en P2P?" antes de reenviar un evento al bot de Telegram
     * "Cuentas P2P" — ver el filtro agregado ahí (agosto 2026).
     */
    @Override
    public Optional<AccountCop> buscarCuentaBancolombiaPorNombre(String nombreCuenta) {
        if (nombreCuenta == null || nombreCuenta.isBlank()) return Optional.empty();

        List<AccountCop> cuentasBancolombia = accountCopRepository.findByBankType(BankType.BANCOLOMBIA);

        // Agrupa por nombre normalizado — si dos cuentas normalizan igual, el
        // nombre queda AMBIGUO y se trata como "no encontrado" en vez de
        // arriesgarse a actualizar/devolver la cuenta equivocada.
        Map<String, List<AccountCop>> porNombre = new HashMap<>();
        for (AccountCop c : cuentasBancolombia) {
            porNombre.computeIfAbsent(normalizar(c.getName()), k -> new ArrayList<>()).add(c);
        }

        List<AccountCop> candidatos = porNombre.get(normalizar(nombreCuenta));
        if (candidatos == null || candidatos.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(candidatos.get(0));
    }

    @Override
    @Transactional
    public boolean registrarResultadoCuenta(String nombreCuenta, boolean disponible,
            Double saldoRealBanco, String motivoError) {
        if (nombreCuenta == null || nombreCuenta.isBlank()) return false;

        Optional<AccountCop> encontrada = buscarCuentaBancolombiaPorNombre(nombreCuenta);
        if (encontrada.isEmpty()) {
            log.warn("[Conciliacion] '{}' no encontrada o ambigua — se ignora.", nombreCuenta);
            return false;
        }

        AccountCop cuenta = encontrada.get();
        cuenta.setUltimaConciliacion(LocalDateTime.now());
        cuenta.setDisponibleBanco(disponible);

        if (disponible) {
            cuenta.setUltimoErrorConciliacion(null);
            Double saldoPochonance = cuenta.getBalance();
            cuenta.setUltimoDesfaseBanco(
                    (saldoRealBanco != null && saldoPochonance != null)
                            ? Math.round((saldoRealBanco - saldoPochonance) * 100.0) / 100.0
                            : null);

            // Auto-desbloqueo: si un bloqueo manual anterior (bloqueadaPorBot=true, de
            // cuando esto SÍ auto-bloqueaba) sigue puesto y ahora la cuenta responde
            // bien, se desbloquea sola. Si bloqueada=true pero NO fue el bot quien la
            // bloqueó, no se toca — un humano la desbloquea cuando quiera.
            if (Boolean.TRUE.equals(cuenta.getBloqueada()) && Boolean.TRUE.equals(cuenta.getBloqueadaPorBot())) {
                cuenta.setBloqueada(false);
                cuenta.setBloqueadaPorBot(false);
            }
        } else {
            cuenta.setUltimoDesfaseBanco(null);
            cuenta.setUltimoErrorConciliacion(motivoError);

            // A PROPÓSITO (fix agosto 2026): esto YA NO bloquea la cuenta automáticamente.
            // Antes sí lo hacía, y un fallo del BOT (ej. sin credenciales de Bitwarden en
            // la máquina que corría el chequeo, no un bloqueo real del banco) terminó
            // bloqueando en cadena TODAS las cuentas de un tirón — ver incidente
            // 15/08/2026 documentado en el bot. "No pude acceder" no es lo mismo que "el
            // banco confirmó que está bloqueada". Bloquear de verdad sigue siendo SIEMPRE
            // una acción manual desde Saldos → "Bloquear cuenta".
            //
            // Lo que SÍ hace ahora (agosto 2026, confirmado con Milton): si la cuenta
            // seguía activa en P2P, se desactiva — no tiene sentido seguir mandándole
            // ventas a una cuenta que el bot no pudo confirmar que sirve. Esto es MUCHO
            // más suave que bloquear: no toca `bloqueada`, se puede reactivar a mano en
            // cualquier momento, y al desactivarse dispara la detención del monitoreo
            // (ver CuentaP2PSyncService) para no dejar la sesión de Chrome corriendo sin
            // motivo.
            if (Boolean.TRUE.equals(cuenta.getActivaParaP2P())) {
                cuenta.setActivaParaP2P(false);
                accountCopRepository.save(cuenta);
                detencionService.solicitarDetencion(cuenta);
                log.warn("[Conciliacion] '{}' desactivada de P2P automáticamente — el bot no pudo confirmarla ({}).",
                        cuenta.getName(), motivoError);
            }
        }

        accountCopRepository.save(cuenta);
        return true;
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

        // Dedupe: si ya hay una pendiente para esta cuenta, no duplicar (ver
        // el comentario en ConciliacionSolicitudRepository.findFirstByCuentaAndConsumidaFalse).
        if (conciliacionSolicitudRepository.findFirstByCuentaAndConsumidaFalse(cuenta.getName()).isPresent()) {
            log.info("[Conciliacion] Ya había una solicitud pendiente para '{}' — no se duplica.", cuenta.getName());
            return;
        }

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
        // Incidente 31/08/2026: ESTE es el método real que consume el bot vía
        // GET /conciliacion/pendiente (el que llama pochonance_activador.py
        // para "activación" — /movimientos/activacion/pendiente, atendido por
        // ActivacionServiceImpl, está muerto, nada lo consume hoy). Tenía el
        // mismo problema que ya se había corregido en ActivacionServiceImpl/
        // DetencionServiceImpl pero en el archivo equivocado: nunca expiraba
        // ni se revalidaba contra el estado actual, así que al arrancar el bot
        // se le entregaba TODO el historial de solicitudes viejas de una vez,
        // incluidas cuentas que ya no estaban seleccionadas en P2P (de ahí el
        // aluvión de activaciones y los crashes de RAM en Chrome). Ahora se
        // revisa el activaParaP2P real antes de entregar cada una.
        Optional<ConciliacionSolicitud> siguiente;
        while ((siguiente = conciliacionSolicitudRepository.findFirstByConsumidaFalseOrderByCreadaEnAsc()).isPresent()) {
            ConciliacionSolicitud solicitud = siguiente.get();
            solicitud.setConsumida(true);
            conciliacionSolicitudRepository.save(solicitud);

            String cuenta = solicitud.getCuenta();
            // buscarCuentaBancolombiaPorNombre ya tolera nombres duplicados/
            // ambiguos (los trata como "no encontrada" en vez de reventar) —
            // ver el método más arriba en esta misma clase.
            boolean activaEnP2PAhora = buscarCuentaBancolombiaPorNombre(cuenta)
                    .map(AccountCop::getActivaParaP2P)
                    .map(Boolean.TRUE::equals)
                    .orElse(false);

            if (activaEnP2PAhora) {
                return Optional.of(cuenta);
            }
            log.info("[Conciliacion] Descartada solicitud de activación obsoleta de '{}' — ya no está activa en P2P "
                    + "(se encoló en su momento, pero el estado cambió antes de que el bot la consumiera).", cuenta);
        }
        return Optional.empty();
    }
}
