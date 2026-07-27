package com.binance.web;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Entity.DetalleRetiro;
import com.binance.web.Entity.SolicitudRetiro;
import com.binance.web.Entity.TipoRetiro;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.Repository.RetiradorRepository;
import com.binance.web.Repository.SolicitudRetiroRepository;
import com.binance.web.service.TelegramService;
import com.binance.web.serviceImpl.RetiradorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas del retiro automático por P2P pedido por Milton: cuando una cuenta
 * está seleccionada en P2P (activaParaP2P) y su saldo alcanza lo que queda
 * disponible de cupo HOY (cajero y/o corresponsal, según cupoTipoP2P), se
 * dispara sola una Solicitud General — igual que si alguien le diera clic a
 * "Solicitud general" — sin duplicar si ya hay una pendiente.
 *
 * Incluye a propósito varios casos con valores "erróneos" (cuentas nulas,
 * bankType nulo, cupos negativos, balances negativos, tipos de cupo
 * inventados, fallas del repositorio) para confirmar que el disparador falla
 * SIEMPRE hacia el lado seguro: si algo no cuadra, simplemente no dispara —
 * nunca lanza una excepción hacia quien lo llama (AccountCopServiceImpl,
 * que no debe romperse por esto) ni dispara de más.
 */
@ExtendWith(MockitoExtension.class)
class RetiroAutomaticoP2PTest {

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    @Mock private RetiradorRepository retiradorRepository;
    @Mock private SolicitudRetiroRepository solicitudRepository;
    @Mock private AccountCopRepository accountCopRepository;
    @Mock private EfectivoRepository efectivoRepository;
    @Mock private MovimientoRepository movimientoRepository;
    @Mock private TelegramService telegramService;

    @InjectMocks
    private RetiradorServiceImpl service;

    private AccountCop cuenta;

    @BeforeEach
    void setUp() {
        cuenta = new AccountCop();
        cuenta.setId(1);
        cuenta.setName("David");
        cuenta.setBankType(BankType.BANCOLOMBIA); // cajero=2.700 / corresponsal=10.000
        cuenta.setActivaParaP2P(true);
        cuenta.setCupoTipoP2P("CAJERO");
        cuenta.setCupoFecha(LocalDate.now(ZONE_BOGOTA)); // "ya se revisó hoy" — no debe resetear el cupo a medio camino
    }

    /** Stub por defecto para que crearSolicitudGeneral() encuentre la cuenta y no haya nada más comprometido. */
    private void stubFlujoFeliz() {
        when(accountCopRepository.findById(1)).thenReturn(Optional.of(cuenta));
        when(solicitudRepository.sumComprometidoPorCuenta(1)).thenReturn(0.0);
        when(solicitudRepository.sumMontoCajeroComprometidoPorCuenta(1)).thenReturn(0.0);
        // lenient: solo se invoca de verdad cuando el canal CORRESPONSAL también
        // dispara (ej. tipo AMBOS) — en los casos de un solo canal (cajero) este
        // stub queda sin usar, y no debe hacer fallar el test por "stub innecesario".
        lenient().when(solicitudRepository.sumMontoCorresponsalComprometidoPorCuenta(1)).thenReturn(0.0);
        when(solicitudRepository.save(any(SolicitudRetiro.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Casos "sanos": debe disparar cuando corresponde ────────────

    @Test
    void balanceLlegaExactoAlDisponible_disparaPorElMontoExacto() {
        cuenta.setBalance(2700.0);
        cuenta.setCupoCajeroDisponibleHoy(2700.0);
        stubFlujoFeliz();

        service.verificarYDispararRetiroAutomaticoP2P(cuenta);

        ArgumentCaptor<SolicitudRetiro> captor = ArgumentCaptor.forClass(SolicitudRetiro.class);
        verify(solicitudRepository, times(1)).save(captor.capture());
        SolicitudRetiro creada = captor.getValue();
        assertEquals(1, creada.getDetalles().size());
        DetalleRetiro d = creada.getDetalles().get(0);
        assertEquals(TipoRetiro.CAJERO, d.getTipoRetiro());
        assertEquals(2700.0, d.getMontoCajero(), 0.001);
        assertEquals(2700.0, creada.getTotalMonto(), 0.001);
    }

    @Test
    void balanceSuperaElDisponible_pideSoloElDisponibleYDejaElExcedenteEnLaCuenta() {
        // Ejemplo real de Milton: cuenta en 2.500, llega una transferencia de 1.000 -> 3.500.
        cuenta.setBalance(3500.0);
        cuenta.setCupoCajeroDisponibleHoy(2700.0);
        stubFlujoFeliz();

        service.verificarYDispararRetiroAutomaticoP2P(cuenta);

        ArgumentCaptor<SolicitudRetiro> captor = ArgumentCaptor.forClass(SolicitudRetiro.class);
        verify(solicitudRepository, times(1)).save(captor.capture());
        DetalleRetiro d = captor.getValue().getDetalles().get(0);
        assertEquals(2700.0, d.getMontoCajero(), 0.001, "debe pedir el tope, no el saldo completo de 3.500");
    }

    @Test
    void yaSeRetiroAlgoHoy_disparaContraElDisponibleRestanteNoElTopeCompleto() {
        // Ejemplo de Milton: ya se retiraron $200 hoy en cajero -> disponible = 2.500, no 2.700.
        cuenta.setCupoCajeroDisponibleHoy(2500.0);
        cuenta.setBalance(2500.0);
        stubFlujoFeliz();

        service.verificarYDispararRetiroAutomaticoP2P(cuenta);

        ArgumentCaptor<SolicitudRetiro> captor = ArgumentCaptor.forClass(SolicitudRetiro.class);
        verify(solicitudRepository, times(1)).save(captor.capture());
        DetalleRetiro d = captor.getValue().getDetalles().get(0);
        assertEquals(2500.0, d.getMontoCajero(), 0.001,
                "debe pedir el disponible restante de HOY (2.500), no el tope completo (2.700)");
    }

    @Test
    void tipoAmbos_revisaCajeroYCorresponsalPorSeparadoYPuedeDispararLosDos() {
        cuenta.setCupoTipoP2P("AMBOS");
        cuenta.setCupoCajeroDisponibleHoy(2700.0);
        cuenta.setCupoCorresponsalDisponibleHoy(10000.0);
        cuenta.setBalance(10000.0); // cubre ambos topes a la vez
        stubFlujoFeliz();

        service.verificarYDispararRetiroAutomaticoP2P(cuenta);

        ArgumentCaptor<SolicitudRetiro> captor = ArgumentCaptor.forClass(SolicitudRetiro.class);
        verify(solicitudRepository, times(2)).save(captor.capture());
        List<SolicitudRetiro> creadas = captor.getAllValues();
        boolean tieneCajero = creadas.stream().anyMatch(s ->
                s.getDetalles().get(0).getTipoRetiro() == TipoRetiro.CAJERO
                        && Math.abs(s.getDetalles().get(0).getMontoCajero() - 2700.0) < 0.001);
        boolean tieneCorresponsal = creadas.stream().anyMatch(s ->
                s.getDetalles().get(0).getTipoRetiro() == TipoRetiro.CORRESPONSAL
                        && Math.abs(s.getDetalles().get(0).getMontoCorresponsal() - 10000.0) < 0.001);
        assertTrue(tieneCajero, "debe disparar el canal cajero");
        assertTrue(tieneCorresponsal, "debe disparar el canal corresponsal");
    }

    @Test
    void cupoTipoNull_seTrataComoAmbosYRevisaLosDosCanales() {
        cuenta.setCupoTipoP2P(null);
        cuenta.setCupoCajeroDisponibleHoy(2700.0);
        cuenta.setCupoCorresponsalDisponibleHoy(10000.0);
        cuenta.setBalance(2700.0); // solo alcanza para cajero, no para corresponsal
        stubFlujoFeliz();

        service.verificarYDispararRetiroAutomaticoP2P(cuenta);

        verify(solicitudRepository, times(1)).save(any(SolicitudRetiro.class));
    }

    @Test
    void montoConDecimalesRaros_seRedondeaA2Decimales() {
        // Valor "erróneo" que no debería existir en COP (fracciones de peso),
        // simulando drift de punto flotante en otra parte del sistema.
        cuenta.setCupoCajeroDisponibleHoy(1234.567);
        cuenta.setBalance(1234.567);
        stubFlujoFeliz();

        service.verificarYDispararRetiroAutomaticoP2P(cuenta);

        ArgumentCaptor<SolicitudRetiro> captor = ArgumentCaptor.forClass(SolicitudRetiro.class);
        verify(solicitudRepository, times(1)).save(captor.capture());
        double monto = captor.getValue().getDetalles().get(0).getMontoCajero();
        assertEquals(1234.57, monto, 0.001, "el monto pedido debe quedar redondeado a 2 decimales");
    }

    // ── Casos que NO deben disparar (sin romper nada) ──────────────

    @Test
    void cuentaNula_noHaceNadaYNoRevienta() {
        assertDoesNotThrow(() -> service.verificarYDispararRetiroAutomaticoP2P(null));
        verifyNoInteractions(solicitudRepository);
    }

    @Test
    void cuentaInactivaEnP2P_noDisparaAunqueElSaldoSeaEnorme() {
        cuenta.setActivaParaP2P(false);
        cuenta.setBalance(999_999_999.0);
        cuenta.setCupoCajeroDisponibleHoy(2700.0);

        assertDoesNotThrow(() -> service.verificarYDispararRetiroAutomaticoP2P(cuenta));
        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void cuentaSinBankType_noDisparaNiRevienta() {
        cuenta.setBankType(null);
        cuenta.setBalance(999_999.0);
        cuenta.setCupoCajeroDisponibleHoy(2700.0);

        assertDoesNotThrow(() -> service.verificarYDispararRetiroAutomaticoP2P(cuenta));
        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void balanceTodaviaNoLlegaAlDisponible_noDispara() {
        cuenta.setBalance(2000.0);
        cuenta.setCupoCajeroDisponibleHoy(2700.0);

        service.verificarYDispararRetiroAutomaticoP2P(cuenta);

        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void cupoYaAgotadoHoy_noDisparaMasAunqueElSaldoSigaSubiendo() {
        // Ya se agotó el cupo de cajero hoy (0 disponible) — no debe volver a
        // disparar por el resto del día aunque sigan llegando ventas P2P.
        cuenta.setCupoCajeroDisponibleHoy(0.0);
        cuenta.setBalance(999_999.0);

        service.verificarYDispararRetiroAutomaticoP2P(cuenta);

        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void cupoDisponibleNegativo_valorCorrupto_noDisparaNiRevienta() {
        // Valor "erróneo": un cupo disponible negativo no debería existir,
        // pero si aparece por algún bug en otro lado, no debe disparar un
        // retiro absurdo ni lanzar una excepción.
        cuenta.setCupoCajeroDisponibleHoy(-100.0);
        cuenta.setBalance(999_999.0);

        assertDoesNotThrow(() -> service.verificarYDispararRetiroAutomaticoP2P(cuenta));
        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void balanceNegativo_cuentaCorrupta_noDisparaNiRevienta() {
        // Ej. una cuenta con saldo corrupto (negativo) — no debe intentar
        // "retirar" nada ni reventar por comparar un negativo contra el cupo.
        cuenta.setBalance(-50_000.0);
        cuenta.setCupoCajeroDisponibleHoy(2700.0);

        assertDoesNotThrow(() -> service.verificarYDispararRetiroAutomaticoP2P(cuenta));
        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void balanceNulo_seTrataComoCeroYNoDispara() {
        cuenta.setBalance(null);
        cuenta.setCupoCajeroDisponibleHoy(2700.0);

        assertDoesNotThrow(() -> service.verificarYDispararRetiroAutomaticoP2P(cuenta));
        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void cupoTipoP2PConValorBasura_noDisparaNiRevienta() {
        // Valor inválido / inventado en cupoTipoP2P — no coincide con
        // CAJERO/CORRESPONSAL/AMBOS, así que no debe disparar por ningún
        // canal (falla hacia el lado seguro: no dispara, en vez de asumir).
        cuenta.setCupoTipoP2P("XYZ_INVALIDO");
        cuenta.setCupoCajeroDisponibleHoy(2700.0);
        cuenta.setCupoCorresponsalDisponibleHoy(10000.0);
        cuenta.setBalance(999_999.0);

        assertDoesNotThrow(() -> service.verificarYDispararRetiroAutomaticoP2P(cuenta));
        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void yaHayUnaSolicitudPendienteDeEseCanal_noDuplica() {
        cuenta.setBalance(2700.0);
        cuenta.setCupoCajeroDisponibleHoy(2700.0);
        // Ya hay $500 comprometidos en cajero en otra solicitud SIN_ASIGNAR/PENDIENTE.
        when(solicitudRepository.sumMontoCajeroComprometidoPorCuenta(1)).thenReturn(500.0);

        service.verificarYDispararRetiroAutomaticoP2P(cuenta);

        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void crearSolicitudGeneralFalla_noPropagaLaExcepcionHaciaElLlamador() {
        // Simulamos una falla real (ej. la cuenta desapareció justo antes de
        // guardar): crearSolicitudGeneral lanza RuntimeException. El método
        // NO debe dejar que esa excepción se escape — quien llama a esto
        // (AccountCopServiceImpl.saveAccountCopSafe) no se puede romper por
        // un fallo en esta funcionalidad secundaria.
        cuenta.setBalance(2700.0);
        cuenta.setCupoCajeroDisponibleHoy(2700.0);
        when(accountCopRepository.findById(1)).thenReturn(Optional.empty());
        when(solicitudRepository.sumMontoCajeroComprometidoPorCuenta(1)).thenReturn(0.0);

        assertDoesNotThrow(() -> service.verificarYDispararRetiroAutomaticoP2P(cuenta));
    }
}
