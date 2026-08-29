package com.binance.web;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.detencion.DetencionService;
import com.binance.web.conciliacion.ConciliacionBancariaServiceImpl;
import com.binance.web.conciliacion.ConciliacionBotChat;
import com.binance.web.conciliacion.ConciliacionBotChatRepository;
import com.binance.web.conciliacion.ConciliacionBotTelegramClient;
import com.binance.web.conciliacion.ConciliacionResponseDto;
import com.binance.web.conciliacion.ConciliacionResultadoDto;
import com.binance.web.conciliacion.ConciliacionSolicitud;
import com.binance.web.conciliacion.ConciliacionSolicitudRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas del endpoint de conciliación bancaria: emparejamiento de cuentas por
 * nombre (con tildes/mayúsculas distintas), cálculo del desfase con el saldo
 * EN VIVO de Pochonance (no el que manda el bot), y manejo de casos raros
 * (nombre que no existe, nombre ambiguo, datos incompletos) sin romper nada.
 *
 * También cubre el "auto-trigger": registrar el chat_id del bot
 * (registrarChat), encolar + consumir una solicitud pendiente cuando se
 * activa una cuenta en "Cuentas P2P" (solicitarConciliacion /
 * obtenerYConsumirPendiente) — el mecanismo real que usa el bot, vía
 * polling, ya que nunca podría enterarse leyendo sus propios mensajes de
 * Telegram.
 */
@ExtendWith(MockitoExtension.class)
class ConciliacionBancariaServiceImplTest {

    @Mock private AccountCopRepository accountCopRepository;
    @Mock private ConciliacionBotChatRepository conciliacionBotChatRepository;
    @Mock private ConciliacionBotTelegramClient conciliacionBotTelegramClient;
    @Mock private ConciliacionSolicitudRepository conciliacionSolicitudRepository;
    @Mock private DetencionService detencionService;

    @InjectMocks
    private ConciliacionBancariaServiceImpl service;

    private AccountCop victor;
    private AccountCop ana;

    @BeforeEach
    void setUp() {
        victor = new AccountCop();
        victor.setId(1);
        victor.setName("Víctor Martínez");
        victor.setBankType(BankType.BANCOLOMBIA);
        victor.setBalance(622.0);

        ana = new AccountCop();
        ana.setId(2);
        ana.setName("Ana");
        ana.setBankType(BankType.BANCOLOMBIA);
        ana.setBalance(1269.0);
    }

    private ConciliacionResultadoDto.Item item(String cuenta, Boolean disponible, Double saldoRealBanco, String error) {
        ConciliacionResultadoDto.Item it = new ConciliacionResultadoDto.Item();
        it.setCuenta(cuenta);
        it.setDisponible(disponible);
        it.setSaldoRealBanco(saldoRealBanco);
        it.setError(error);
        return it;
    }

    private ConciliacionResultadoDto request(ConciliacionResultadoDto.Item... items) {
        ConciliacionResultadoDto dto = new ConciliacionResultadoDto();
        dto.setResultados(List.of(items));
        return dto;
    }

    @Test
    void empareja_ignorandoTildesYMayusculas() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(victor, ana));

        // El bot manda "victor martinez" (sin tilde, minusculas) — debe emparejar con "Víctor Martínez".
        ConciliacionResponseDto resp = service.procesarResultado(
                request(item("victor martinez", true, 615.51, null)));

        assertEquals(1, resp.getActualizados().size());
        assertTrue(resp.getNoEncontrados().isEmpty());
        assertTrue(Boolean.TRUE.equals(victor.getDisponibleBanco()));
        assertNotNull(victor.getUltimaConciliacion());
        verify(accountCopRepository).save(victor);
    }

    @Test
    void calculaDesfaseConElSaldoEnVivoDePochonanceNoConElDelBot() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));
        ana.setBalance(1269.0); // saldo EN VIVO en Pochonance al momento de procesar

        service.procesarResultado(request(item("Ana", true, 1250.0, null)));

        // desfase = saldoRealBanco - balance EN VIVO = 1250 - 1269 = -19
        assertEquals(-19.0, ana.getUltimoDesfaseBanco(), 0.001);
    }

    @Test
    void cuentaNoDisponible_guardaElErrorYDesfaseQuedaNulo() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        ConciliacionResponseDto resp = service.procesarResultado(
                request(item("Ana", false, null, "timeout esperando login")));

        assertEquals(1, resp.getActualizados().size());
        assertFalse(ana.getDisponibleBanco());
        assertNull(ana.getUltimoDesfaseBanco());
        assertEquals("timeout esperando login", ana.getUltimoErrorConciliacion());
    }

    @Test
    void cuentaNoDisponible_yaNoSeBloqueaAutomaticamente_soloDejaAvisoParaRevisionManual() {
        // Fix agosto 2026: antes esto bloqueaba la cuenta con solo un fallo de
        // login del bot — causó un incidente real (bloqueos en cadena por
        // credenciales mal configuradas en la máquina del bot, no por cuentas
        // realmente bloqueadas por el banco). "No pude acceder" != "el banco
        // confirmó bloqueo" — ahora eso queda registrado en
        // disponibleBanco/ultimoErrorConciliacion (ver test de arriba) y
        // bloquear de verdad ('bloqueada') es siempre una acción manual desde
        // Saldos → "Bloquear cuenta".
        ana.setActivaParaP2P(true);
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        service.procesarResultado(request(item("Ana", false, null, "error leyendo Bancolombia")));

        assertFalse(ana.getBloqueada());
        assertFalse(ana.getBloqueadaPorBot());
    }

    @Test
    void cuentaNoDisponible_activaEnP2P_seDesactivaDeP2PYAvisaAlBotQueSeDetenga() {
        // Confirmado con Milton (agosto 2026): esto SÍ debe desactivar de P2P
        // (activaParaP2P=false) — distinto de bloquear ('bloqueada' sigue en
        // false, se puede reactivar a mano en cualquier momento). No tiene
        // sentido seguir mandándole ventas a una cuenta que el bot no pudo
        // confirmar que sirve. Al desactivarse, dispara CuentaP2PSyncService
        // para que Movimientos pare esa sesión puntual.
        ana.setActivaParaP2P(true);
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        service.procesarResultado(request(item("Ana", false, null, "error leyendo Bancolombia")));

        assertFalse(ana.getActivaParaP2P(), "el bot no pudo confirmar la cuenta — debe salir de P2P");
        assertFalse(ana.getBloqueada(), "desactivar de P2P no es lo mismo que bloquear la cuenta");
        verify(detencionService).solicitarDetencion(ana);
    }

    @Test
    void cuentaBloqueadaPorElBot_seDesbloqueaSolaCuandoVuelveALeerseBien() {
        ana.setBloqueada(true);
        ana.setBloqueadaPorBot(true);
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        service.procesarResultado(request(item("Ana", true, 1250.0, null)));

        assertFalse(ana.getBloqueada());
        assertFalse(ana.getBloqueadaPorBot());
    }

    @Test
    void cuentaBloqueadaManualmente_elBotNuncaLaDesbloqueaAunqueLaLeaBien() {
        ana.setBloqueada(true);
        ana.setBloqueadaPorBot(false); // bloqueo manual desde Saldos, no del bot
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        service.procesarResultado(request(item("Ana", true, 1250.0, null)));

        assertTrue(ana.getBloqueada(), "un bloqueo manual no lo debe tocar el bot");
        assertFalse(ana.getBloqueadaPorBot());
    }

    @Test
    void cuentaNiBloqueadaNiTocadaPorElBot_disponibleTrue_noCambiaNadaDeBloqueo() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        service.procesarResultado(request(item("Ana", true, 1250.0, null)));

        assertFalse(ana.getBloqueada());
        assertFalse(ana.getBloqueadaPorBot());
    }

    @Test
    void nombreQueNoExisteEnPochonance_seReportaComoNoEncontradoSinRomper() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana, victor));

        ConciliacionResponseDto resp = service.procesarResultado(
                request(item("Cuenta Fantasma", true, 100.0, null)));

        assertTrue(resp.getActualizados().isEmpty());
        assertEquals(List.of("Cuenta Fantasma"), resp.getNoEncontrados());
        verify(accountCopRepository, never()).save(any());
    }

    @Test
    void nombreAmbiguo_dosCuentasConElMismoNombreNormalizado_noActualizaNinguna() {
        AccountCop anaDuplicada = new AccountCop();
        anaDuplicada.setId(3);
        anaDuplicada.setName("ana"); // normaliza igual que "Ana"
        anaDuplicada.setBankType(BankType.BANCOLOMBIA);
        anaDuplicada.setBalance(50.0);

        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana, anaDuplicada));

        ConciliacionResponseDto resp = service.procesarResultado(
                request(item("Ana", true, 100.0, null)));

        assertEquals(List.of("Ana"), resp.getNoEncontrados(),
                "nombre ambiguo: mejor no actualizar ninguna que arriesgarse a la cuenta equivocada");
        verify(accountCopRepository, never()).save(any());
    }

    @Test
    void saldoRealBancoNulo_aunqueDisponibleSeaTrue_desfaseQuedaNuloSinRomper() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        assertDoesNotThrow(() -> service.procesarResultado(
                request(item("Ana", true, null, null))));

        assertNull(ana.getUltimoDesfaseBanco());
    }

    @Test
    void balancePochonanceNulo_cuentaConDatoCorrupto_noRompe() {
        ana.setBalance(null);
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        assertDoesNotThrow(() -> service.procesarResultado(
                request(item("Ana", true, 100.0, null))));

        assertNull(ana.getUltimoDesfaseBanco());
    }

    @Test
    void nombreVacioOEnNulo_noRompeYSeReportaComoNoEncontrado() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        ConciliacionResponseDto resp = assertDoesNotThrow(() -> service.procesarResultado(
                request(item(null, true, 100.0, null))));

        assertEquals(1, resp.getNoEncontrados().size());
        verify(accountCopRepository, never()).save(any());
    }

    @Test
    void listaDeResultadosVacia_noRompeYNoLlamaAlRepositorio() {
        ConciliacionResponseDto resp = assertDoesNotThrow(() -> service.procesarResultado(request()));

        assertTrue(resp.getActualizados().isEmpty());
        assertTrue(resp.getNoEncontrados().isEmpty());
        verifyNoInteractions(accountCopRepository);
    }

    @Test
    void requestNulo_noRompeYDevuelveRespuestaVacia() {
        ConciliacionResponseDto resp = assertDoesNotThrow(() -> service.procesarResultado(null));

        assertTrue(resp.getActualizados().isEmpty());
        assertTrue(resp.getNoEncontrados().isEmpty());
        verifyNoInteractions(accountCopRepository);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // registrarChat / solicitarConciliacion — auto-trigger desde "Cuentas P2P"
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void registrarChat_primeraVez_creaLaFilaConIdFijo() {
        when(conciliacionBotChatRepository.findById(1)).thenReturn(Optional.empty());

        service.registrarChat(555L);

        ArgumentCaptor<ConciliacionBotChat> captor = ArgumentCaptor.forClass(ConciliacionBotChat.class);
        verify(conciliacionBotChatRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getId());
        assertEquals(555L, captor.getValue().getChatId());
        assertNotNull(captor.getValue().getRegistradoEn());
    }

    @Test
    void registrarChat_yaExistiaUno_loReemplazaEnVezDeCrearOtro() {
        ConciliacionBotChat existente = new ConciliacionBotChat();
        existente.setId(1);
        existente.setChatId(111L);
        when(conciliacionBotChatRepository.findById(1)).thenReturn(Optional.of(existente));

        service.registrarChat(999L);

        verify(conciliacionBotChatRepository).save(existente);
        assertEquals(999L, existente.getChatId());
    }

    @Test
    void registrarChat_chatIdNulo_noHaceNadaNiRompe() {
        assertDoesNotThrow(() -> service.registrarChat(null));
        verifyNoInteractions(conciliacionBotChatRepository);
    }

    @Test
    void solicitarConciliacion_encolaUnaSolicitudPendienteConElNombreDeLaCuenta() {
        when(conciliacionBotChatRepository.findById(1)).thenReturn(Optional.empty());

        service.solicitarConciliacion(ana);

        ArgumentCaptor<ConciliacionSolicitud> captor = ArgumentCaptor.forClass(ConciliacionSolicitud.class);
        verify(conciliacionSolicitudRepository).save(captor.capture());
        assertEquals("Ana", captor.getValue().getCuenta());
        assertFalse(captor.getValue().isConsumida());
        assertNotNull(captor.getValue().getCreadaEn());
    }

    @Test
    void solicitarConciliacion_conChatRegistrado_tambienMandaAvisoInformativoPorTelegram() {
        ConciliacionBotChat chat = new ConciliacionBotChat();
        chat.setId(1);
        chat.setChatId(777L);
        when(conciliacionBotChatRepository.findById(1)).thenReturn(Optional.of(chat));

        service.solicitarConciliacion(ana);

        ArgumentCaptor<String> textoCaptor = ArgumentCaptor.forClass(String.class);
        verify(conciliacionBotTelegramClient).enviarMensaje(eq(777L), textoCaptor.capture());
        assertTrue(textoCaptor.getValue().contains("Ana"),
                "el mensaje debe traer el nombre de la cuenta: " + textoCaptor.getValue());
    }

    @Test
    void solicitarConciliacion_sinChatRegistradoTodavia_llamaAlClienteConNulYNoRompe() {
        when(conciliacionBotChatRepository.findById(1)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.solicitarConciliacion(ana));

        verify(conciliacionBotTelegramClient).enviarMensaje(eq((Long) null), any());
    }

    @Test
    void solicitarConciliacion_cuentaNula_noRompeNiEncolaNiLlamaAlCliente() {
        assertDoesNotThrow(() -> service.solicitarConciliacion(null));
        verifyNoInteractions(conciliacionBotTelegramClient);
        verifyNoInteractions(conciliacionSolicitudRepository);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // obtenerYConsumirPendiente — polling del bot (reemplaza el intento fallido
    // de "escuchar" el aviso por Telegram)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void obtenerYConsumirPendiente_hayUnaPendiente_laDevuelveYLaMarcaConsumida() {
        ConciliacionSolicitud pendiente = new ConciliacionSolicitud();
        pendiente.setId(10L);
        pendiente.setCuenta("Jorge Sanchez");
        pendiente.setConsumida(false);
        when(conciliacionSolicitudRepository.findFirstByConsumidaFalseOrderByCreadaEnAsc())
                .thenReturn(Optional.of(pendiente));

        Optional<String> resultado = service.obtenerYConsumirPendiente();

        assertEquals(Optional.of("Jorge Sanchez"), resultado);
        assertTrue(pendiente.isConsumida());
        verify(conciliacionSolicitudRepository).save(pendiente);
    }

    @Test
    void obtenerYConsumirPendiente_noHayNinguna_devuelveVacioSinRomper() {
        when(conciliacionSolicitudRepository.findFirstByConsumidaFalseOrderByCreadaEnAsc())
                .thenReturn(Optional.empty());

        Optional<String> resultado = assertDoesNotThrow(() -> service.obtenerYConsumirPendiente());

        assertTrue(resultado.isEmpty());
        verify(conciliacionSolicitudRepository, never()).save(any());
    }
}
