package com.binance.web;

import com.binance.web.BinanceAPI.AsignacionAutomaticaService;
import com.binance.web.BinanceAPI.P2PActiveOrderService;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.AutoAsignacionConfigRepository;
import com.binance.web.Repository.P2PPreAsignacionRepository;
import com.binance.web.activacion.CuentaP2PSyncService;
import com.binance.web.dto.ActiveP2POrderDto;
import com.binance.web.movimientosbridge.MovimientosCuentasPendientes;
import com.binance.web.service.AccountCopService;
import com.binance.web.util.VentanaCupoP2P;
import com.binance.web.util.VentanaCupoP2P.Canal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de la asignación automática: ventana horaria (corresponsal de día, cajero desde 18:30)
 * y "no repetidas" (mismo monto exacto en la misma cuenta mientras la venta siga en curso).
 * Montos en MILES de COP.
 */
@ExtendWith(MockitoExtension.class)
class AsignacionAutomaticaReglasTest {

    @Mock private P2PActiveOrderService activeOrderService;
    @Mock private AccountCopRepository accountCopRepository;
    @Mock private CuentaP2PSyncService cuentaP2PSyncService;
    @Mock private AutoAsignacionConfigRepository configRepository;
    @Mock private AccountCopService accountCopService;
    @Mock private MovimientosCuentasPendientes cuentasPendientes;
    @Mock private P2PPreAsignacionRepository preAsignacionRepository;

    @InjectMocks private AsignacionAutomaticaService servicio;

    private static Clock a(int h, int m) {
        return Clock.fixed(
                LocalDate.of(2026, 10, 6).atTime(LocalTime.of(h, m)).atZone(VentanaCupoP2P.ZONA).toInstant(),
                VentanaCupoP2P.ZONA);
    }

    private void horaDelDia(int h, int m) {
        ReflectionTestUtils.setField(servicio, "reloj", a(h, m));
    }

    /** Cuenta Bancolombia activa en P2P con cupos de HOY ya al día. */
    private AccountCop cuenta(int id, double saldo, double cupoCorresponsal, double cupoCajero) {
        AccountCop c = new AccountCop();
        c.setId(id);
        c.setName("Cuenta " + id);
        c.setBankType(BankType.BANCOLOMBIA);
        c.setActivaParaP2P(true);
        c.setBloqueada(false);
        c.setBalance(saldo);
        c.setCupoFecha(LocalDate.now(VentanaCupoP2P.ZONA));
        c.setCupoCorresponsalDisponibleHoy(cupoCorresponsal);
        c.setCupoCajeroDisponibleHoy(cupoCajero);
        return c;
    }

    private ActiveP2POrderDto orden(String numero, double pesosMiles, Integer preAsignadoCopId) {
        ActiveP2POrderDto o = new ActiveP2POrderDto();
        o.setOrderNumber(numero);
        o.setAccountBinance("Luis");
        o.setPesosCop(pesosMiles);
        o.setPreAsignadoCopId(preAsignadoCopId);
        o.setCreateTime("2026-10-06T10:00:00Z");
        return o;
    }

    @BeforeEach
    void init() {
        horaDelDia(10, 0); // por defecto: de día
    }

    // ── Ventana horaria ───────────────────────────────────────────

    @Test
    void ventana_limitesHorarios() {
        assertEquals(Canal.CORRESPONSAL, VentanaCupoP2P.canalEn(LocalTime.of(0, 0)));
        assertEquals(Canal.CORRESPONSAL, VentanaCupoP2P.canalEn(LocalTime.of(18, 29, 59)));
        assertEquals(Canal.CAJERO, VentanaCupoP2P.canalEn(LocalTime.of(18, 30)));
        assertEquals(Canal.CAJERO, VentanaCupoP2P.canalEn(LocalTime.of(23, 59, 59)));
    }

    @Test
    void deDia_soloMiraCorresponsal_ignoraCajero() {
        // A: corresponsal casi lleno (restan 1.000 de espacio) pero cajero con mucho espacio.
        // B: corresponsal con 3.000 de espacio. Venta de 500: de día manda corresponsal -> A es la mas cercana.
        AccountCop A = cuenta(1, 9_000, 10_000, 2_700);
        AccountCop B = cuenta(2, 7_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(A, B));

        servicio.asignar(List.of(orden("o1", 500, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(1), any(), any());
    }

    @Test
    void deNoche_soloMiraCajero_ignoraCorresponsal() {
        horaDelDia(19, 0);
        // Corresponsal: A esta casi llena. Cajero: B esta mas cerca de su limite (restan 700 vs 2.000).
        AccountCop A = cuenta(1, 1_000, 10_000, 3_000); // espacio cajero 2.000
        AccountCop B = cuenta(2, 1_000, 10_000, 1_700); // espacio cajero 700
        when(accountCopRepository.findAll()).thenReturn(List.of(A, B));

        servicio.asignar(List.of(orden("o1", 500, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(2), any(), any());
    }

    @Test
    void a1829_todaviaEsCorresponsal_ya1830_esCajero() {
        AccountCop A = cuenta(1, 9_000, 10_000, 100_000); // corresponsal: espacio 1.000; cajero: enorme
        AccountCop B = cuenta(2, 1_000, 10_000, 1_800);   // corresponsal: espacio 9.000; cajero: 800
        when(accountCopRepository.findAll()).thenReturn(List.of(A, B));

        horaDelDia(18, 29);
        servicio.asignar(List.of(orden("o1", 500, null)));
        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(1), any(), any());

        horaDelDia(18, 30);
        servicio.asignar(List.of(orden("o2", 500, null)));
        verify(activeOrderService).upsertPreAsignacion(eq("o2"), eq(2), any(), any());
    }

    // ── Mejor ajuste (ejemplos del cliente) ───────────────────────

    @Test
    void ventaDe4M_vaALaDe6M_noALaDe9M() {
        AccountCop de9 = cuenta(1, 9_000, 10_000, 2_700);
        AccountCop de6 = cuenta(2, 6_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(de9, de6));

        servicio.asignar(List.of(orden("o1", 4_000, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(2), any(), any());
    }

    @Test
    void ventaDe1M_vaALaDe9M() {
        AccountCop de9 = cuenta(1, 9_000, 10_000, 2_700);
        AccountCop de6 = cuenta(2, 6_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(de9, de6));

        servicio.asignar(List.of(orden("o1", 1_000, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(1), any(), any());
    }

    // ── No repetidas ──────────────────────────────────────────────

    @Test
    void mismoMontoAbierto_vaAOtraCuenta() {
        AccountCop juan = cuenta(1, 9_000, 10_000, 2_700);  // la mas cercana al limite
        AccountCop pedro = cuenta(2, 5_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(juan, pedro));

        // Ya hay una venta en curso de 100 asignada a Juan; llega otra de 100.
        servicio.asignar(List.of(orden("viva", 100, 1), orden("nueva", 100, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("nueva"), eq(2), any(), any());
    }

    @Test
    void mismoMonto_dentroDelMismoCiclo_seReparteEntreCuentas() {
        AccountCop c1 = cuenta(1, 9_000, 10_000, 2_700);
        AccountCop c2 = cuenta(2, 8_000, 10_000, 2_700);
        AccountCop c3 = cuenta(3, 7_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(c1, c2, c3));

        List<ActiveP2POrderDto> ventas = new ArrayList<>();
        for (int i = 1; i <= 3; i++) ventas.add(orden("v" + i, 100, null));
        servicio.asignar(ventas);

        verify(activeOrderService).upsertPreAsignacion(eq("v1"), eq(1), any(), any());
        verify(activeOrderService).upsertPreAsignacion(eq("v2"), eq(2), any(), any());
        verify(activeOrderService).upsertPreAsignacion(eq("v3"), eq(3), any(), any());
    }

    @Test
    void montoDistinto_puedeIrALaMismaCuenta() {
        AccountCop juan = cuenta(1, 9_000, 10_000, 2_700);
        AccountCop pedro = cuenta(2, 5_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(juan, pedro));

        servicio.asignar(List.of(orden("viva", 100, 1), orden("nueva", 150, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("nueva"), eq(1), any(), any());
    }

    @Test
    void ventaYaCerrada_noBloqueaElMonto() {
        // La orden liberada ya no viene en la lista de "en curso": el monto vuelve a estar libre.
        AccountCop juan = cuenta(1, 9_000, 10_000, 2_700);
        AccountCop pedro = cuenta(2, 5_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(juan, pedro));

        servicio.asignar(List.of(orden("nueva", 100, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("nueva"), eq(1), any(), any());
    }

    @Test
    void soloUnaCuentaConMontoAbierto_laVentaQuedaSinAsignar() {
        AccountCop juan = cuenta(1, 9_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(juan));

        servicio.asignar(List.of(orden("viva", 100, 1), orden("nueva", 100, null)));

        verify(activeOrderService, never()).upsertPreAsignacion(eq("nueva"), any(), any(), any());
    }

    // ── Selección de cuentas por el Auto ──────────────────────────

    private AccountCop inactiva(int id, double saldo) {
        AccountCop c = cuenta(id, saldo, 10_000, 2_700);
        c.setActivaParaP2P(false);
        return c;
    }

    @Test
    void sinCuentasActivas_alVenirUnaVenta_eligeLasCuentasYLaAsigna() {
        AccountCop c1 = inactiva(1, 9_000);
        AccountCop c2 = inactiva(2, 5_000);
        when(accountCopRepository.findAll()).thenReturn(List.of(c1, c2));
        // La seleccion real activa las cuentas: aqui se simula marcandolas activas.
        when(accountCopService.activarCincoCuentasMasCercanasAlCupo()).thenAnswer(inv -> {
            c1.setActivaParaP2P(true);
            c2.setActivaParaP2P(true);
            return List.of(c1, c2);
        });

        servicio.asignar(List.of(orden("o1", 1_000, null)));

        verify(accountCopService).activarCincoCuentasMasCercanasAlCupo();
        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(1), any(), any());
    }

    @Test
    void conCuentasYaActivas_noEligeOtras() {
        AccountCop activa = cuenta(1, 9_000, 10_000, 2_700);
        AccountCop otra = inactiva(2, 5_000);
        when(accountCopRepository.findAll()).thenReturn(List.of(activa, otra));

        servicio.asignar(List.of(orden("o1", 500, null)));

        verify(accountCopService, never()).activarCincoCuentasMasCercanasAlCupo();
        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(1), any(), any());
    }

    @Test
    void sinCuentasActivas_yNingunaCandidata_noAsignaNada() {
        AccountCop c1 = inactiva(1, 9_900); // le queda menos del sublimite
        when(accountCopRepository.findAll()).thenReturn(List.of(c1));
        when(accountCopService.activarCincoCuentasMasCercanasAlCupo()).thenReturn(List.of());

        servicio.asignar(List.of(orden("o1", 500, null)));

        verify(activeOrderService, never()).upsertPreAsignacion(any(), any(), any(), any());
    }

    @Test
    void alPrenderElAuto_sinCuentasActivas_lasElige() {
        AccountCop c1 = inactiva(1, 9_000);
        when(configRepository.findById(1)).thenReturn(java.util.Optional.empty());
        when(accountCopRepository.findAll()).thenReturn(List.of(c1));
        when(accountCopService.activarCincoCuentasMasCercanasAlCupo()).thenReturn(List.of(c1));

        servicio.setActiva(true);

        verify(accountCopService).activarCincoCuentasMasCercanasAlCupo();
    }

    @Test
    void alPrenderElAuto_conCuentasActivas_noElige() {
        AccountCop activa = cuenta(1, 9_000, 10_000, 2_700);
        when(configRepository.findById(1)).thenReturn(java.util.Optional.empty());
        when(accountCopRepository.findAll()).thenReturn(List.of(activa));

        servicio.setActiva(true);

        verify(accountCopService, never()).activarCincoCuentasMasCercanasAlCupo();
    }

    @Test
    void alApagarElAuto_noEligeNada() {
        when(configRepository.findById(1)).thenReturn(java.util.Optional.empty());

        servicio.setActiva(false);

        verify(accountCopService, never()).activarCincoCuentasMasCercanasAlCupo();
    }

    // ── Solo Bancolombia ──────────────────────────────────────────

    @Test
    void cuentaNequi_noRecibeVentas_aunqueEsteActiva() {
        AccountCop nequi = cuenta(1, 9_000, 10_000, 2_700);
        nequi.setBankType(BankType.NEQUI);
        AccountCop banco = cuenta(2, 5_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(nequi, banco));

        servicio.asignar(List.of(orden("o1", 500, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(2), any(), any());
    }

    @Test
    void soloHayNequiActivas_cuentaComoSinCuentas_yEligeLasBancolombia() {
        AccountCop nequi = cuenta(1, 9_000, 10_000, 2_700);
        nequi.setBankType(BankType.NEQUI);
        AccountCop banco = inactiva(2, 5_000);
        when(accountCopRepository.findAll()).thenReturn(List.of(nequi, banco));
        when(accountCopService.activarCincoCuentasMasCercanasAlCupo()).thenAnswer(inv -> {
            banco.setActivaParaP2P(true);
            return List.of(banco);
        });

        servicio.asignar(List.of(orden("o1", 500, null)));

        verify(accountCopService).activarCincoCuentasMasCercanasAlCupo();
        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(2), any(), any());
    }

    // ── Esperar la confirmacion de Movimientos ────────────────────

    @Test
    void cuentaPendienteDeConfirmacion_noRecibeVentas() {
        AccountCop pendiente = cuenta(1, 9_000, 10_000, 2_700); // la mas cercana al limite
        AccountCop lista = cuenta(2, 5_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(pendiente, lista));
        when(cuentasPendientes.estaPendiente("Cuenta 1")).thenReturn(true);

        servicio.asignar(List.of(orden("o1", 500, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(2), any(), any());
    }

    @Test
    void todasPendientes_laVentaQuedaSinAsignar() {
        AccountCop c1 = cuenta(1, 9_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(c1));
        when(cuentasPendientes.estaPendiente("Cuenta 1")).thenReturn(true);

        servicio.asignar(List.of(orden("o1", 500, null)));

        verify(activeOrderService, never()).upsertPreAsignacion(any(), any(), any(), any());
    }

    // ── Cuenta llena por saldo que entra ──────────────────────────

    @Test
    void cuentaLlenaConPlataReal_sinVentasAbiertas_saleDelGrupoYSeReemplaza() {
        AccountCop llena = cuenta(1, 10_100, 10_000, 2_700);   // llego al limite (espacio -100)
        AccountCop candidata = inactiva(2, 3_000);
        when(accountCopRepository.findAll()).thenReturn(List.of(llena, candidata));

        servicio.asignar(List.of()); // ni siquiera hay ventas en curso

        assertEquals(false, llena.getActivaParaP2P());
        assertEquals(true, candidata.getActivaParaP2P());
        verify(cuentaP2PSyncService).sincronizar(llena, true);
        verify(cuentaP2PSyncService).sincronizar(candidata, false);
    }

    @Test
    void cuentaLlena_peroConVentaAbierta_seQuedaYSigueMonitoreada() {
        AccountCop llena = cuenta(1, 10_100, 10_000, 2_700);
        AccountCop candidata = inactiva(2, 3_000);
        when(accountCopRepository.findAll()).thenReturn(List.of(llena, candidata));

        servicio.asignar(List.of(orden("abierta", 600, 1)));

        assertEquals(true, llena.getActivaParaP2P());
        verify(cuentaP2PSyncService, never()).sincronizar(any(), eq(true));
    }

    @Test
    void listaDeOrdenesVacia_peroPreAsignacionEnBd_noSacaLaCuenta() {
        AccountCop llena = cuenta(1, 10_100, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(llena));
        P2PPreAsignacionRepository.PreSinImportar pre = org.mockito.Mockito.mock(P2PPreAsignacionRepository.PreSinImportar.class);
        when(pre.getCopId()).thenReturn(1);
        when(preAsignacionRepository.findSinImportar()).thenReturn(List.of(pre));

        servicio.asignar(List.of()); // el poll no devolvio ordenes (p. ej. Binance fallo)

        assertEquals(true, llena.getActivaParaP2P());
        verify(cuentaP2PSyncService, never()).sincronizar(any(), eq(true));
    }

    @Test
    void escenarioDelCliente_500LiberadaY600EnCurso_laNuevaDe500VaAOtraCuenta() {
        // Cuenta en 9.000, entraron 500 (ya liberada -> saldo 9.500) y queda una de 600 sin confirmar.
        AccountCop a = cuenta(1, 9_500, 10_000, 2_700);
        AccountCop otra = cuenta(2, 2_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(a, otra));

        servicio.asignar(List.of(orden("de600", 600, 1), orden("de500", 500, null)));

        // Proyectado: 10.000 - 9.500 - 600 = -100 -> la de 500 no cabe en 'a'.
        verify(activeOrderService).upsertPreAsignacion(eq("de500"), eq(2), any(), any());
        assertEquals(true, a.getActivaParaP2P());                       // sigue activa y monitoreada
        verify(cuentaP2PSyncService, never()).sincronizar(any(), eq(true));
    }

    @Test
    void escenarioDelCliente_laDe600SeCae_laCuentaRecuperaEspacio() {
        // La de 600 ya no esta en curso: saldo sigue en 9.500, espacio 500. La venta de 500 que
        // seguia sin asignar ahora SI cabe en la cuenta (y es la mas cercana al limite).
        AccountCop a = cuenta(1, 9_500, 10_000, 2_700);
        AccountCop otra = cuenta(2, 2_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(a, otra));

        servicio.asignar(List.of(orden("de500", 500, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("de500"), eq(1), any(), any());
    }

    @Test
    void escenarioDelCliente_laDe600SeLibera_laCuentaSaleDelGrupo() {
        // Se libero la de 600: saldo 10.100, ya no hay ventas abiertas -> llego al limite de verdad.
        AccountCop a = cuenta(1, 10_100, 10_000, 2_700);
        AccountCop candidata = inactiva(2, 3_000);
        when(accountCopRepository.findAll()).thenReturn(List.of(a, candidata));

        servicio.asignar(List.of());

        verify(cuentaP2PSyncService).sincronizar(a, true);
        assertEquals(false, a.getActivaParaP2P());
    }

    @Test
    void ventaQueDejaLaCuentaSinEspacio_noLaDesactivaEnseguida() {
        AccountCop a = cuenta(1, 9_000, 10_000, 2_700);   // espacio 1.000
        when(accountCopRepository.findAll()).thenReturn(List.of(a));

        servicio.asignar(List.of(orden("o1", 1_040, null))); // cabe por la tolerancia de 50

        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(1), any(), any());
        assertEquals(true, a.getActivaParaP2P());
        verify(cuentaP2PSyncService, never()).sincronizar(any(), eq(true));
    }

    @Test
    void sinVentasYConEspacio_noHaceNada() {
        AccountCop a = cuenta(1, 5_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(a));

        servicio.asignar(List.of());

        assertEquals(true, a.getActivaParaP2P());
        verify(cuentaP2PSyncService, never()).sincronizar(any(), any(Boolean.class));
    }
}
