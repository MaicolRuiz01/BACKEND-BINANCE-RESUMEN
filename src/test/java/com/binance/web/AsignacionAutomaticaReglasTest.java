package com.binance.web;

import com.binance.web.BinanceAPI.AsignacionAutomaticaService;
import com.binance.web.BinanceAPI.P2PActiveOrderService;
import com.binance.web.BinanceAPI.SaldosEnCursoService;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.AutoAsignacionConfigRepository;
import com.binance.web.activacion.CuentaP2PSyncService;
import com.binance.web.dto.ActiveP2POrderDto;
import com.binance.web.movimientosbridge.MovimientosCuentasPendientes;
import com.binance.web.service.RetiradorService;
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
import static org.mockito.Mockito.lenient;
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
    @Mock private SaldosEnCursoService saldosEnCursoService;
    @Mock private RetiradorService retiradorService;

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
        assertEquals(Canal.CAJERO, VentanaCupoP2P.canalEn(LocalTime.of(18, 40)));
        assertEquals(Canal.CORRESPONSAL, VentanaCupoP2P.canalEn(LocalTime.of(1, 0)));
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
    void sinCuentasActivas_alVenirUnaVenta_abreLasCuentasYLaAsigna() {
        AccountCop c1 = inactiva(1, 9_000);
        AccountCop c2 = inactiva(2, 5_000);
        when(accountCopRepository.findAll()).thenReturn(List.of(c1, c2));

        servicio.asignar(List.of(orden("o1", 1_000, null)));

        // El mantenimiento abre las dos candidatas (no llega a 7: no hay mas) y la venta va a la mas cercana.
        verify(cuentaP2PSyncService).sincronizar(c1, false);
        verify(cuentaP2PSyncService).sincronizar(c2, false);
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
        AccountCop c1 = inactiva(1, 9_900); // le queda menos del sublimite: no es candidata
        when(accountCopRepository.findAll()).thenReturn(List.of(c1));

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

        servicio.asignar(List.of(orden("o1", 500, null)));

        // La Nequi no cuenta como cuenta del grupo: se abre la Bancolombia y es la que recibe la venta.
        verify(cuentaP2PSyncService).sincronizar(banco, false);
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

        assertEquals(true, llena.getActivaParaP2P());                   // sigue activa y monitoreada
        verify(cuentaP2PSyncService, never()).sincronizar(any(), eq(true)); // no se detiene
        assertEquals(true, candidata.getActivaParaP2P());               // y su reposicion se abre de inmediato
    }

    @Test
    void listaDeOrdenesVacia_peroPreAsignacionEnBd_noSacaLaCuenta() {
        AccountCop llena = cuenta(1, 10_100, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(llena));
        when(saldosEnCursoService.calcular()).thenReturn(List.of(enCurso(1, 600, "en-bd")));

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

    // ── Tipo de cupo (icono cajero / corresponsal) ────────────────

    @Test
    void alCambiarLaVentana_lasActivasQueSiguenSirviendoCambianDeMarca_yAMBOSnoSeToca() {
        AccountCop a = cuenta(1, 1_000, 10_000, 2_700);
        a.setCupoTipoP2P("CORRESPONSAL");
        AccountCop b = cuenta(2, 1_000, 10_000, 2_700);
        b.setCupoTipoP2P("AMBOS");
        when(accountCopRepository.findAll()).thenReturn(List.of(a, b));

        horaDelDia(10, 0);
        servicio.asignar(List.of());
        horaDelDia(20, 40); // ya es ventana de cajero
        servicio.asignar(List.of());
        horaDelDia(1, 0);   // y de madrugada, corresponsal
        servicio.asignar(List.of());

        // Terminan en la ventana de la madrugada (corresponsal); a las 20:40 estuvieron en CAJERO.
        assertEquals("CORRESPONSAL", a.getCupoTipoP2P());
        assertEquals("AMBOS", b.getCupoTipoP2P());   // AMBOS no se toca nunca
    }

    @Test
    void laCuentaDeReemplazo_quedaMarcadaConElCanalDeLaHora() {
        horaDelDia(19, 0);
        AccountCop llena = cuenta(1, 3_000, 10_000, 2_700);  // cajero: espacio -300 -> llena
        AccountCop candidata = inactiva(2, 500);              // cajero: espacio 2.200
        candidata.setCupoTipoP2P("AMBOS");
        when(accountCopRepository.findAll()).thenReturn(List.of(llena, candidata));

        servicio.asignar(List.of());

        assertEquals(true, candidata.getActivaParaP2P());
        assertEquals("CAJERO", candidata.getCupoTipoP2P());
    }

    // ── Tolerancia de $500.000 ────────────────────────────────────

    @Test
    void cuentaCasiLlena_recibePrimeroLaVentaQueLaCompleta_yLasDemasVanAOtra() {
        // William: 9.988 de 10.000 (espacio 12). Llegan ventas de 100, 200 y 300.
        // La de 100 completa el cupo (queda en -88, dentro de la tolerancia); desde ahi ya no recibe mas.
        AccountCop william = cuenta(1, 9_988, 10_000, 2_700);
        AccountCop otra = cuenta(2, 3_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(william, otra));

        servicio.asignar(List.of(orden("v100", 100, null), orden("v200", 200, null), orden("v300", 300, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("v100"), eq(1), any(), any()); // 12-100 = -88
        verify(activeOrderService).upsertPreAsignacion(eq("v200"), eq(2), any(), any()); // el cupo ya se completo
        verify(activeOrderService).upsertPreAsignacion(eq("v300"), eq(2), any(), any());
    }

    @Test
    void laTolerancia_esDe500mil_conLimiteInclusivo() {
        // 9.700 de 10.000: faltan 300. Una de 800 la deja en 10.500 (justo el limite); una de 801 ya no cabe.
        AccountCop a = cuenta(1, 9_700, 10_000, 2_700);
        AccountCop b = cuenta(2, 2_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(a, b));

        servicio.asignar(List.of(orden("justo", 800, null)));
        verify(activeOrderService).upsertPreAsignacion(eq("justo"), eq(1), any(), any());

        servicio.asignar(List.of(orden("pasada", 801, null)));
        verify(activeOrderService).upsertPreAsignacion(eq("pasada"), eq(2), any(), any());
    }

    @Test
    void ventaDe2Millones_enCuentaCasiLlena_vaALaSiguiente() {
        AccountCop casiLlena = cuenta(1, 9_500, 10_000, 2_700);
        AccountCop otra = cuenta(2, 3_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(casiLlena, otra));

        servicio.asignar(List.of(orden("grande", 2_000, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("grande"), eq(2), any(), any());
    }

    // ── La tolerancia solo completa el cupo (regla de Milton, 07/10/2026) ──

    @Test
    void tolerancia_cuentaEn9500_ventaDe1000_seMandaYQuedaEn10500() {
        AccountCop a = cuenta(1, 9_500, 10_000, 2_700);
        AccountCop otra = cuenta(2, 1_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(a, otra));

        servicio.asignar(List.of(orden("o1", 1_000, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(1), any(), any());
    }

    @Test
    void tolerancia_cuentaYaPasadaDelCupo_noRecibeMas_aunqueQuepaEnLaTolerancia() {
        // Caso real Leidy: 10.199 con cupo 10.000 y una venta de 240 que la llevaba a 10.439.
        AccountCop leidy = cuenta(1, 10_199, 10_000, 2_700);
        AccountCop otra = cuenta(2, 3_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(leidy, otra));

        // Leidy tiene otra venta abierta (de 50), asi que no sale del grupo y solo se prueba la regla.
        servicio.asignar(List.of(orden("abierta", 50, 1), orden("nueva", 240, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("nueva"), eq(2), any(), any());
        verify(activeOrderService, never()).upsertPreAsignacion(eq("nueva"), eq(1), any(), any());
    }

    @Test
    void tolerancia_dosVentasSeguidas_laPrimeraCompletaElCupo_laSegundaVaAOtra() {
        // Montos distintos a proposito: con montos iguales la bloquearia la regla de repetidas, no esta.
        AccountCop a = cuenta(1, 9_500, 10_000, 2_700);
        AccountCop otra = cuenta(2, 2_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(a, otra));

        servicio.asignar(List.of(orden("o500", 500, null), orden("o400", 400, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("o500"), eq(1), any(), any()); // queda exacto en 10.000
        verify(activeOrderService).upsertPreAsignacion(eq("o400"), eq(2), any(), any()); // el cupo ya esta completo
    }

    @Test
    void tolerancia_cupoExactoCompletado_noRecibeNada() {
        AccountCop a = cuenta(1, 9_500, 10_000, 2_700);
        AccountCop otra = cuenta(2, 2_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(a, otra));

        // 9.500 + 500 abierta = 10.000 exactos: disponible 0 -> ya no recibe, ni siquiera 10.
        servicio.asignar(List.of(orden("abierta", 500, 1), orden("chica", 10, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("chica"), eq(2), any(), any());
    }

    @Test
    void tolerancia_enCajero_cuentaEn2200_ventaDe1000_seMandaYQuedaEn3200() {
        horaDelDia(19, 0);
        AccountCop a = cuenta(1, 2_200, 10_000, 2_700);   // cajero: espacio 500
        AccountCop otra = cuenta(2, 100, 10_000, 2_700);  // cajero: espacio 2.600
        when(accountCopRepository.findAll()).thenReturn(List.of(a, otra));

        servicio.asignar(List.of(orden("o1", 1_000, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(1), any(), any());
    }

    @Test
    void tolerancia_enCajero_cuentaYaPasada_noRecibeMas() {
        horaDelDia(19, 0);
        AccountCop pasada = cuenta(1, 2_900, 10_000, 2_700);  // cajero: -200
        AccountCop otra = cuenta(2, 500, 10_000, 2_700);      // cajero: 2.200
        when(accountCopRepository.findAll()).thenReturn(List.of(pasada, otra));

        servicio.asignar(List.of(orden("abierta", 50, 1), orden("nueva", 240, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("nueva"), eq(2), any(), any());
    }

    // ── Fuente unica de lo "en curso" (misma que el naranja de la pantalla) ──

    private SaldosEnCursoService.SaldoEnCurso enCurso(int copId, double pesosEnCurso, String... ordenes) {
        List<SaldosEnCursoService.DetalleEnCurso> det = new ArrayList<>();
        for (String o : ordenes) det.add(new SaldosEnCursoService.DetalleEnCurso(o, pesosEnCurso / ordenes.length));
        return new SaldosEnCursoService.SaldoEnCurso(copId, 0.0, 2_700.0, 10_000.0, pesosEnCurso, det);
    }

    @Test
    void ordenRecienLiberadaSinImportar_sigueContandoComoComprometida() {
        // La de 600 ya salio de la lista de activas (se libero) pero aun no esta en el saldo.
        AccountCop a = cuenta(1, 9_500, 10_000, 2_700);
        AccountCop otra = cuenta(2, 2_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(a, otra));
        when(saldosEnCursoService.calcular()).thenReturn(List.of(enCurso(1, 600, "liberada-sin-importar")));

        servicio.asignar(List.of(orden("nueva", 500, null)));

        // 10.000 - 9.500 - 600 = -100: ya no cabe nada mas, la venta va a la otra cuenta.
        verify(activeOrderService).upsertPreAsignacion(eq("nueva"), eq(2), any(), any());
    }

    @Test
    void rafagaDeLiberadasSinImportar_laCuentaMasCercanaNoSePasa() {
        // Caso Yeiner: 9.900 de saldo y varias ventas ya liberadas por importar (150 + 130).
        AccountCop yeiner = cuenta(1, 9_900, 10_000, 2_700);
        AccountCop otra = cuenta(2, 3_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(yeiner, otra));
        when(saldosEnCursoService.calcular()).thenReturn(List.of(enCurso(1, 280, "l1", "l2")));

        servicio.asignar(List.of(orden("nueva", 100, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("nueva"), eq(2), any(), any());
    }

    @Test
    void unaOrdenEnLaListaYEnElServicio_noSeCuentaDosVeces() {
        AccountCop a = cuenta(1, 9_500, 10_000, 2_700);   // espacio 500
        AccountCop otra = cuenta(2, 2_000, 10_000, 2_700);
        when(accountCopRepository.findAll()).thenReturn(List.of(a, otra));
        when(saldosEnCursoService.calcular()).thenReturn(List.of(enCurso(1, 400, "x")));

        // La misma orden 'x' (400) viene en la lista de activas y en el servicio.
        servicio.asignar(List.of(orden("x", 400, 1), orden("nueva", 500, null)));

        // Contada una vez: disponible 100 > 0 y 100-500 = -400 (dentro de la tolerancia) -> va a 'a'.
        verify(activeOrderService).upsertPreAsignacion(eq("nueva"), eq(1), any(), any());
    }

    // ── Reposicion inmediata: siempre 7 cuentas que reciban ventas ──

    /** n cuentas activas con espacio de sobra en ambos canales, ids desde 'desde'. */
    private List<AccountCop> activas(int desde, int n) {
        List<AccountCop> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(cuenta(desde + i, 1_000, 10_000, 2_700));
        return l;
    }

    private List<AccountCop> candidatas(int desde, int n) {
        List<AccountCop> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(inactiva(desde + i, 1_000 + i));
        return l;
    }

    private List<AccountCop> juntas(List<AccountCop>... listas) {
        List<AccountCop> l = new ArrayList<>();
        for (List<AccountCop> x : listas) l.addAll(x);
        return l;
    }

    @Test
    @SuppressWarnings("unchecked")
    void casoYeiner_sinCupoConVentaAbierta_seQuedaMonitoreada_perolaReposicionSeAbreYa() {
        // Yeiner: cupo de corresponsal en 0 y 401 en curso. Las otras 6 reciben ventas. Hay una candidata.
        AccountCop yeiner = cuenta(1, 44, 0, 2_700);
        List<AccountCop> otras = activas(2, 6);
        List<AccountCop> cand = candidatas(20, 1);
        when(accountCopRepository.findAll()).thenReturn(juntas(List.of(yeiner), otras, cand));
        when(saldosEnCursoService.calcular()).thenReturn(List.of(enCurso(1, 401, "a", "b", "c")));

        servicio.asignar(List.of());

        assertEquals(true, yeiner.getActivaParaP2P());                    // sigue monitoreada
        assertEquals(true, cand.get(0).getActivaParaP2P());               // la reposicion ya esta abierta
        verify(cuentaP2PSyncService).sincronizar(cand.get(0), false);
        verify(cuentaP2PSyncService, never()).sincronizar(eq(yeiner), eq(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void siempreSiete_conTresActivas_abreCuatroMas() {
        List<AccountCop> act = activas(1, 3);
        List<AccountCop> cand = candidatas(20, 6);
        when(accountCopRepository.findAll()).thenReturn(juntas(act, cand));

        servicio.asignar(List.of());

        long activasAhora = juntas(act, cand).stream().filter(a -> Boolean.TRUE.equals(a.getActivaParaP2P())).count();
        assertEquals(7, activasAhora);
        verify(cuentaP2PSyncService, org.mockito.Mockito.times(4)).sincronizar(any(), eq(false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void conOchoActivas_cierraUnaYNoAbreNinguna() {
        // Antes con 8 no se tocaba nada; ahora el Auto toma el control: 7 es lo sano, la 8ª es solo margen.
        List<AccountCop> act = activas(1, 8);
        List<AccountCop> cand = candidatas(20, 3);
        when(accountCopRepository.findAll()).thenReturn(juntas(act, cand));

        servicio.asignar(List.of());

        assertEquals(7, cuantasActivas(act));
        assertEquals(0, cuantasActivas(cand));
        verify(cuentaP2PSyncService, org.mockito.Mockito.times(1)).sincronizar(any(), eq(true));
        verify(cuentaP2PSyncService, never()).sincronizar(any(), eq(false));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dosCuentasLlenasSinVentas_seCierranLasDos_yAbrenDos() {
        List<AccountCop> sanas = activas(1, 5);
        AccountCop llena1 = cuenta(10, 10_050, 10_000, 2_700);
        AccountCop llena2 = cuenta(11, 10_300, 10_000, 2_700);
        List<AccountCop> cand = candidatas(20, 3);
        when(accountCopRepository.findAll()).thenReturn(juntas(sanas, List.of(llena1, llena2), cand));

        servicio.asignar(List.of());

        assertEquals(false, llena1.getActivaParaP2P());
        assertEquals(false, llena2.getActivaParaP2P());
        long activasAhora = juntas(sanas, List.of(llena1, llena2), cand).stream()
                .filter(a -> Boolean.TRUE.equals(a.getActivaParaP2P())).count();
        assertEquals(7, activasAhora);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cuentaConCupoProyectadoAgotadoPorVentasAbiertas_noCuentaEntreLasSiete_yseRepone() {
        // 9.500 de saldo con 600 en curso: proyectado -100, ya no recibe, aunque su cupo real aun no se cumplio.
        AccountCop casi = cuenta(1, 9_500, 10_000, 2_700);
        List<AccountCop> otras = activas(2, 6);
        List<AccountCop> cand = candidatas(20, 1);
        when(accountCopRepository.findAll()).thenReturn(juntas(List.of(casi), otras, cand));
        when(saldosEnCursoService.calcular()).thenReturn(List.of(enCurso(1, 600, "x")));

        servicio.asignar(List.of());

        assertEquals(true, cand.get(0).getActivaParaP2P());
        assertEquals(true, casi.getActivaParaP2P());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinCandidatas_noReventaYNoActivaNada() {
        List<AccountCop> act = activas(1, 3);
        when(accountCopRepository.findAll()).thenReturn(act);

        servicio.asignar(List.of());
        servicio.asignar(List.of()); // dos ciclos seguidos: no debe pasar nada raro

        verify(cuentaP2PSyncService, never()).sincronizar(any(), any(Boolean.class));
    }

    // ── Pasar a cajero cuando se agota el corresponsal (a cualquier hora) ──

    /** Cuenta sin cupo de corresponsal (agotado) pero con espacio de cajero. */
    private AccountCop sinCorresponsal(int id, boolean activa) {
        AccountCop c = cuenta(id, 500, 0, 2_700); // corresponsal: 0 - 500 = -500; cajero: 2.200
        c.setActivaParaP2P(activa);
        return c;
    }

    @Test
    @SuppressWarnings("unchecked")
    void sinCupoDeCorresponsalEnNingunaCuenta_aLas12_pasaACajeroDeInmediato() {
        horaDelDia(12, 0); // de dia: la hora pediria corresponsal
        List<AccountCop> activas = new ArrayList<>();
        for (int i = 1; i <= 3; i++) activas.add(sinCorresponsal(i, true));
        List<AccountCop> cand = new ArrayList<>();
        for (int i = 20; i < 24; i++) cand.add(sinCorresponsal(i, false));
        when(accountCopRepository.findAll()).thenReturn(juntas(activas, cand));

        servicio.asignar(List.of());

        // Las 4 candidatas se abren por CAJERO (corresponsal ya no tiene capacidad en ninguna cuenta).
        assertEquals(7, juntas(activas, cand).stream().filter(a -> Boolean.TRUE.equals(a.getActivaParaP2P())).count());
        assertEquals("CAJERO", cand.get(0).getCupoTipoP2P());
        verify(cuentaP2PSyncService, org.mockito.Mockito.times(4)).sincronizar(any(), eq(false));
    }

    @Test
    void sinCupoDeCorresponsal_laVentaSeAsignaConElCupoDeCajero() {
        horaDelDia(12, 0);
        AccountCop a = sinCorresponsal(1, true);   // cajero: espacio 2.200
        AccountCop b = sinCorresponsal(2, true);
        b.setBalance(1_000.0);                      // cajero: espacio 1.700 -> la mas cercana al limite
        when(accountCopRepository.findAll()).thenReturn(List.of(a, b));

        servicio.asignar(List.of(orden("o1", 300, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("o1"), eq(2), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void siAunHayUnaCuentaConCupoDeCorresponsal_seSigueEnCorresponsal() {
        horaDelDia(12, 0);
        List<AccountCop> activas = new ArrayList<>();
        for (int i = 1; i <= 3; i++) activas.add(sinCorresponsal(i, true));
        AccountCop conCupo = inactiva(30, 1_000);   // corresponsal: espacio 9.000 -> sigue habiendo capacidad
        when(accountCopRepository.findAll()).thenReturn(juntas(activas, List.of(conCupo)));

        servicio.asignar(List.of());

        assertEquals(true, conCupo.getActivaParaP2P());
        assertEquals("CORRESPONSAL", conCupo.getCupoTipoP2P());
    }

    @Test
    void alDiaSiguiente_conCuposNuevos_vuelveACorresponsal() {
        horaDelDia(12, 0);
        AccountCop a = sinCorresponsal(1, true);
        AccountCop cand = sinCorresponsal(2, false);
        when(accountCopRepository.findAll()).thenReturn(List.of(a, cand));
        servicio.asignar(List.of());
        assertEquals("CAJERO", cand.getCupoTipoP2P());   // hoy: cajero

        // Cupos nuevos (como tras el reset de medianoche): ahora si hay corresponsal.
        cand.setActivaParaP2P(false);
        cand.setCupoCorresponsalDisponibleHoy(10_000.0);
        cand.setCupoTipoP2P("AMBOS");
        horaDelDia(0, 30);
        servicio.asignar(List.of());

        assertEquals("CORRESPONSAL", cand.getCupoTipoP2P());
    }

    // ── A las 18:30 el salto a cajero es inmediato ────────────────

    @Test
    @SuppressWarnings("unchecked")
    void alas1830_lasSieteSinEspacioDeCajeroSeCierran_yAbrenSieteConEspacio() {
        // 7 cuentas con 5.000 de saldo: de dia (corresponsal) tienen espacio 5.000; de noche (cajero 2.700) no.
        List<AccountCop> grupo = new ArrayList<>();
        for (int i = 1; i <= 7; i++) grupo.add(cuenta(i, 5_000, 10_000, 2_700));
        List<AccountCop> nuevas = candidatas(20, 7); // saldo ~1.000: espacio de cajero 1.700
        when(accountCopRepository.findAll()).thenReturn(juntas(grupo, nuevas));

        horaDelDia(18, 29);
        servicio.asignar(List.of());
        verify(cuentaP2PSyncService, never()).sincronizar(any(), any(Boolean.class)); // a las 18:29 nada cambia

        horaDelDia(18, 30);
        servicio.asignar(List.of());

        verify(cuentaP2PSyncService, org.mockito.Mockito.times(7)).sincronizar(any(), eq(true));   // cierran 7
        verify(cuentaP2PSyncService, org.mockito.Mockito.times(7)).sincronizar(any(), eq(false));  // abren 7
        assertEquals("CAJERO", nuevas.get(0).getCupoTipoP2P());
        assertEquals(false, grupo.get(0).getActivaParaP2P());
    }

    @Test
    @SuppressWarnings("unchecked")
    void alas1830_unaCuentaConVentaAbierta_seQuedaMonitoreadaPeroYaNoCuenta() {
        AccountCop conVenta = cuenta(1, 5_000, 10_000, 2_700);
        List<AccountCop> resto = new ArrayList<>();
        for (int i = 2; i <= 7; i++) resto.add(cuenta(i, 500, 10_000, 2_700)); // cajero: espacio 2.200
        List<AccountCop> nuevas = candidatas(20, 2);
        when(accountCopRepository.findAll()).thenReturn(juntas(List.of(conVenta), resto, nuevas));
        when(saldosEnCursoService.calcular()).thenReturn(List.of(enCurso(1, 300, "en-curso")));

        horaDelDia(18, 30);
        servicio.asignar(List.of());

        assertEquals(true, conVenta.getActivaParaP2P());          // sigue vigilada (espera su venta)
        assertEquals(1, nuevas.stream().filter(a -> Boolean.TRUE.equals(a.getActivaParaP2P())).count()); // y su reemplazo ya esta abierto (la mas cercana al limite)
    }

    // ── Cambio de canal: las cuentas que siguen sirviendo cambian de marca SIN avisar a Movimientos ──

    private List<AccountCop> siete(double saldo, String tipo) {
        List<AccountCop> l = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            AccountCop c = cuenta(i, saldo, 10_000, 2_700);
            c.setCupoTipoP2P(tipo);
            l.add(c);
        }
        return l;
    }

    @Test
    void alas1830_laCuentaQueSigueSirviendoParaCajero_cambiaDeMarca_sinAvisarAMovimientos() {
        // Caso del jefe: a las 18:20 la cuenta estaba como corresponsal con 1.500 de saldo; a las 18:30 sirve para cajero.
        List<AccountCop> grupo = siete(1_500, "CORRESPONSAL");
        when(accountCopRepository.findAll()).thenReturn(grupo);

        horaDelDia(18, 29);
        servicio.asignar(List.of());
        assertEquals("CORRESPONSAL", grupo.get(0).getCupoTipoP2P());

        horaDelDia(18, 30);
        servicio.asignar(List.of());

        for (AccountCop c : grupo) {
            assertEquals("CAJERO", c.getCupoTipoP2P());
            assertEquals(true, c.getActivaParaP2P());          // sigue activa
        }
        verify(cuentaP2PSyncService, never()).sincronizar(any(), any(Boolean.class)); // Movimientos no se entera
    }

    @Test
    void alas1830_laCuentaQueNoSirveParaCajero_peroTieneVentaAbierta_noCambiaDeMarca() {
        List<AccountCop> grupo = siete(1_500, "CORRESPONSAL");
        AccountCop sinEspacio = cuenta(8, 5_000, 10_000, 2_700);   // cajero: -2.300
        sinEspacio.setCupoTipoP2P("CORRESPONSAL");
        List<AccountCop> todas = new ArrayList<>(grupo);
        todas.add(sinEspacio);
        when(accountCopRepository.findAll()).thenReturn(todas);
        when(saldosEnCursoService.calcular()).thenReturn(List.of(enCurso(8, 300, "venta")));

        horaDelDia(18, 30);
        servicio.asignar(List.of());

        assertEquals("CAJERO", grupo.get(0).getCupoTipoP2P());
        assertEquals("CORRESPONSAL", sinEspacio.getCupoTipoP2P());   // no sirve para cajero: se queda como esta
        assertEquals(true, sinEspacio.getActivaParaP2P());           // y sigue vigilada por su venta
    }

    @Test
    void dentroDeLaMismaVentana_noSePisaUnCambioManual_yAMBOSnoSeToca() {
        List<AccountCop> grupo = siete(1_500, "CORRESPONSAL");
        grupo.get(6).setCupoTipoP2P("AMBOS");
        when(accountCopRepository.findAll()).thenReturn(grupo);

        horaDelDia(18, 30);
        servicio.asignar(List.of());
        assertEquals("CAJERO", grupo.get(0).getCupoTipoP2P());
        assertEquals("AMBOS", grupo.get(6).getCupoTipoP2P());

        grupo.get(0).setCupoTipoP2P("CORRESPONSAL");   // el operador la cambia a mano
        horaDelDia(19, 0);
        servicio.asignar(List.of());

        assertEquals("CORRESPONSAL", grupo.get(0).getCupoTipoP2P());
    }

    @Test
    void alAcabarseElCorresponsalDeTodas_lasQueSirvenParaCajeroCambianSinAvisarAMovimientos() {
        horaDelDia(12, 0);
        List<AccountCop> grupo = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            AccountCop c = sinCorresponsal(i, true);        // corresponsal agotado, cajero con espacio
            c.setCupoTipoP2P("CORRESPONSAL");
            grupo.add(c);
        }
        when(accountCopRepository.findAll()).thenReturn(grupo);

        servicio.asignar(List.of());

        for (AccountCop c : grupo) assertEquals("CAJERO", c.getCupoTipoP2P());
        verify(cuentaP2PSyncService, never()).sincronizar(any(), any(Boolean.class));
    }

    // ── Retiro de corte de las 18:30 (gancho del Auto) ────────────

    private void corteConfigurado(boolean habilitado) {
        ReflectionTestUtils.setField(servicio, "corteHabilitado", habilitado);
        ReflectionTestUtils.setField(servicio, "corteMinimoMiles", 500.0);
    }

    @Test
    void corte_alas1830_seDisparaUnaSolaVezConLasCuentasActivas() {
        corteConfigurado(true);
        List<AccountCop> grupo = siete(1_500, "CORRESPONSAL");
        when(accountCopRepository.findAll()).thenReturn(grupo);

        horaDelDia(18, 29);
        servicio.asignar(List.of());
        verify(retiradorService, never()).solicitarRetiroCorteCorresponsal(any(), org.mockito.ArgumentMatchers.anyDouble());

        horaDelDia(18, 30);
        servicio.asignar(List.of());
        horaDelDia(18, 31);
        servicio.asignar(List.of());
        horaDelDia(19, 0);
        servicio.asignar(List.of());

        verify(retiradorService, org.mockito.Mockito.times(1))
                .solicitarRetiroCorteCorresponsal(org.mockito.ArgumentMatchers.argThat(l -> l.size() == 7), eq(500.0));
    }

    @Test
    void corte_seHaceAntesDeCerrarLasCuentas_porEsoIncluyeLasQueDespuesSeCierran() {
        corteConfigurado(true);
        List<AccountCop> grupo = siete(5_000, "CORRESPONSAL"); // sin espacio de cajero: se cerraran a las 18:30
        List<AccountCop> nuevas = candidatas(20, 7);
        when(accountCopRepository.findAll()).thenReturn(juntas(grupo, nuevas));

        horaDelDia(18, 30);
        servicio.asignar(List.of());

        // Aunque despues se cierran, el corte las vio activas: pidio el retiro de las 7.
        verify(retiradorService).solicitarRetiroCorteCorresponsal(
                org.mockito.ArgumentMatchers.argThat(l -> l.size() == 7), eq(500.0));
        assertEquals(false, grupo.get(0).getActivaParaP2P());
    }

    @Test
    void corte_siElBackendArrancaMuyTardeEnLaNoche_noSeDisparaFueraDeLaVentana() {
        corteConfigurado(true);
        when(accountCopRepository.findAll()).thenReturn(siete(1_500, "CORRESPONSAL"));

        horaDelDia(21, 0);
        servicio.asignar(List.of());

        verify(retiradorService, never()).solicitarRetiroCorteCorresponsal(any(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void corte_deDia_noSeDispara() {
        corteConfigurado(true);
        when(accountCopRepository.findAll()).thenReturn(siete(1_500, "CORRESPONSAL"));

        horaDelDia(12, 0);
        servicio.asignar(List.of());

        verify(retiradorService, never()).solicitarRetiroCorteCorresponsal(any(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void corte_apagadoPorConfiguracion_noSeDispara() {
        corteConfigurado(false);
        when(accountCopRepository.findAll()).thenReturn(siete(1_500, "CORRESPONSAL"));

        horaDelDia(18, 30);
        servicio.asignar(List.of());

        verify(retiradorService, never()).solicitarRetiroCorteCorresponsal(any(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void corte_noIncluyeCuentasInactivasNiQueNoSonBancolombia() {
        corteConfigurado(true);
        List<AccountCop> grupo = siete(1_500, "CORRESPONSAL");
        AccountCop nequi = cuenta(30, 1_500, 10_000, 2_700);
        nequi.setBankType(BankType.NEQUI);
        AccountCop inactiva = inactiva(31, 1_500);
        when(accountCopRepository.findAll()).thenReturn(juntas(grupo, List.of(nequi, inactiva)));

        horaDelDia(18, 30);
        servicio.asignar(List.of());

        verify(retiradorService).solicitarRetiroCorteCorresponsal(
                org.mockito.ArgumentMatchers.argThat(l -> l.size() == 7 && !l.contains(nequi) && !l.contains(inactiva)), eq(500.0));
    }

    // ── Rescate en cajero: una venta es una venta ─────────────────

    private List<AccountCop> cuentasCajero(double... saldos) {
        List<AccountCop> l = new ArrayList<>();
        for (int i = 0; i < saldos.length; i++) l.add(cuenta(i + 1, saldos[i], 10_000, 2_700));
        return l;
    }

    @Test
    void rescate_cajero_ventaDe5Millones_vaALaCuentaConMasEspacio() {
        horaDelDia(19, 0);
        // Saldos 1.000 / 2.000 / 1.500 / 100: ninguna cabe una venta de 5.000, ni con tolerancia.
        List<AccountCop> cuentas = cuentasCajero(1_000, 2_000, 1_500, 100);
        when(accountCopRepository.findAll()).thenReturn(cuentas);

        servicio.asignar(List.of(orden("grande", 5_000, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("grande"), eq(4), any(), any()); // la de 100
    }

    @Test
    void rescate_cajero_siLaVentaCabeEnAlgunaCuenta_noHayRescate_vaALaMasCercanaAlLimite() {
        horaDelDia(19, 0);
        List<AccountCop> cuentas = cuentasCajero(1_000, 2_000, 1_500, 100);
        when(accountCopRepository.findAll()).thenReturn(cuentas);

        servicio.asignar(List.of(orden("normal", 600, null)));

        // Cabe en varias: la mas cercana al limite entre las que caben es la de 2.000 (espacio 700).
        verify(activeOrderService).upsertPreAsignacion(eq("normal"), eq(2), any(), any());
    }

    @Test
    void rescate_soloEnCajero_enCorresponsalLaVentaQueNoCabeQuedaSinAsignar() {
        horaDelDia(12, 0);
        List<AccountCop> cuentas = cuentasCajero(1_000, 2_000, 1_500, 100);
        when(accountCopRepository.findAll()).thenReturn(cuentas);

        servicio.asignar(List.of(orden("enorme", 12_000, null))); // corresponsal: ninguna cabe 12.000

        verify(activeOrderService, never()).upsertPreAsignacion(eq("enorme"), any(), any(), any());
    }

    @Test
    void rescate_respetaLasNoRepetidas_unaCuentaConLaMismaVentaAbiertaSeSalta() {
        horaDelDia(19, 0);
        AccountCop a = cuenta(1, 100, 10_000, 2_700);    // con 3.000 abiertos: espacio -400 (la mejor del rescate)
        AccountCop b = cuenta(2, 3_500, 10_000, 2_700);  // con una venta abierta de 100: se mantiene en el grupo
        when(accountCopRepository.findAll()).thenReturn(List.of(a, b));

        servicio.asignar(List.of(orden("abierta", 3_000, 1), orden("otra", 100, 2), orden("nueva", 3_000, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("nueva"), eq(2), any(), any()); // a se salta
    }

    @Test
    void rescate_siTodasTienenLaMismaVentaAbierta_seAsignaIgualALaDeMasEspacio() {
        horaDelDia(19, 0);
        AccountCop a = cuenta(1, 100, 10_000, 2_700);    // con 3.000 abiertos: espacio -400
        AccountCop b = cuenta(2, 3_500, 10_000, 2_700);  // con 3.000 abiertos: espacio -3.800
        when(accountCopRepository.findAll()).thenReturn(List.of(a, b));

        servicio.asignar(List.of(orden("o1", 3_000, 1), orden("o2", 3_000, 2), orden("nueva", 3_000, null)));

        // Las dos tienen una venta de 3.000 abierta: una venta hay que asignarla, va a la de mas espacio.
        verify(activeOrderService).upsertPreAsignacion(eq("nueva"), eq(1), any(), any());
    }

    @Test
    void rescate_siNingunaCuentaEstaConfirmadaPorMovimientos_quedaSinAsignar() {
        horaDelDia(19, 0);
        List<AccountCop> cuentas = cuentasCajero(1_000, 2_000);
        when(accountCopRepository.findAll()).thenReturn(cuentas);
        lenient().when(cuentasPendientes.estaPendiente("Cuenta 1")).thenReturn(true);
        lenient().when(cuentasPendientes.estaPendiente("Cuenta 2")).thenReturn(true);

        servicio.asignar(List.of(orden("grande", 5_000, null)));

        verify(activeOrderService, never()).upsertPreAsignacion(eq("grande"), any(), any(), any());
    }

    @Test
    void rescate_noUsaCuentasPendientesDeConfirmacion() {
        horaDelDia(19, 0);
        List<AccountCop> cuentas = cuentasCajero(1_000, 2_000, 1_500, 100);
        when(accountCopRepository.findAll()).thenReturn(cuentas);
        lenient().when(cuentasPendientes.estaPendiente("Cuenta 4")).thenReturn(true); // la de 100 aun no la abrio Movimientos

        servicio.asignar(List.of(orden("grande", 5_000, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("grande"), eq(1), any(), any()); // la siguiente con mas espacio
    }

    @Test
    @SuppressWarnings("unchecked")
    void rescate_noUsaCuentasInactivas_aunqueTenganMasEspacio() {
        horaDelDia(19, 0);
        List<AccountCop> activas = new ArrayList<>();
        for (int i = 1; i <= 7; i++) activas.add(cuenta(i, 2_000, 10_000, 2_700)); // 7 recibiendo: sin reposicion
        AccountCop vacia = inactiva(30, 0);                                        // la mas vacia, pero inactiva
        when(accountCopRepository.findAll()).thenReturn(juntas(activas, List.of(vacia)));

        servicio.asignar(List.of(orden("grande", 5_000, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("grande"), eq(1), any(), any());
        verify(activeOrderService, never()).upsertPreAsignacion(eq("grande"), eq(30), any(), any());
    }

    // ── Tope de hierro: nunca mas de 8 cuentas activas ────────────

    @Test
    @SuppressWarnings("unchecked")
    void tope_conOchoActivasPeroSoloAlgunasRecibiendo_noAbreNingunaMas() {
        horaDelDia(19, 0);
        // 5 reciben ventas (espacio de cajero) y 3 ya no reciben pero siguen vigiladas por ventas abiertas.
        List<AccountCop> recibiendo = new ArrayList<>();
        for (int i = 1; i <= 5; i++) recibiendo.add(cuenta(i, 500, 10_000, 2_700));
        List<AccountCop> esperando = new ArrayList<>();
        for (int i = 6; i <= 8; i++) esperando.add(cuenta(i, 5_000, 10_000, 2_700));   // cajero: -2.300
        List<AccountCop> cand = candidatas(20, 5);
        when(accountCopRepository.findAll()).thenReturn(juntas(recibiendo, esperando, cand));
        when(saldosEnCursoService.calcular()).thenReturn(List.of(
                enCurso(6, 300, "a"), enCurso(7, 300, "b"), enCurso(8, 300, "c")));

        servicio.asignar(List.of());

        // Recibiendo son 5 (< 7), pero ya hay 8 activas: el tope de hierro gana, no se abre ninguna.
        verify(cuentaP2PSyncService, never()).sincronizar(any(), eq(false));
        for (AccountCop c : cand) assertEquals(false, c.getActivaParaP2P());
    }

    @Test
    @SuppressWarnings("unchecked")
    void tope_conSeisActivas_abreSoloHastaCompletarOcho() {
        horaDelDia(19, 0);
        List<AccountCop> recibiendo = new ArrayList<>();
        for (int i = 1; i <= 4; i++) recibiendo.add(cuenta(i, 500, 10_000, 2_700));
        List<AccountCop> esperando = new ArrayList<>();
        for (int i = 5; i <= 6; i++) esperando.add(cuenta(i, 5_000, 10_000, 2_700));
        List<AccountCop> cand = candidatas(20, 6);
        when(accountCopRepository.findAll()).thenReturn(juntas(recibiendo, esperando, cand));
        when(saldosEnCursoService.calcular()).thenReturn(List.of(enCurso(5, 300, "a"), enCurso(6, 300, "b")));

        servicio.asignar(List.of());

        long activasAhora = juntas(recibiendo, esperando, cand).stream()
                .filter(a -> Boolean.TRUE.equals(a.getActivaParaP2P())).count();
        assertEquals(8, activasAhora);   // 6 + 2 nuevas; el objetivo de 7 recibiendo no puede pasar del tope
    }

    @Test
    @SuppressWarnings("unchecked")
    void tope_lasQueSeCierranLiberanCupoParaAbrirOtras() {
        horaDelDia(19, 0);
        // 8 activas, 3 llenas sin ventas abiertas: se cierran y se pueden abrir reemplazos sin pasar de 8.
        List<AccountCop> sanas = new ArrayList<>();
        for (int i = 1; i <= 5; i++) sanas.add(cuenta(i, 500, 10_000, 2_700));
        List<AccountCop> llenas = new ArrayList<>();
        for (int i = 6; i <= 8; i++) llenas.add(cuenta(i, 5_000, 10_000, 2_700));
        List<AccountCop> cand = candidatas(20, 6);
        when(accountCopRepository.findAll()).thenReturn(juntas(sanas, llenas, cand));

        servicio.asignar(List.of());

        long activasAhora = juntas(sanas, llenas, cand).stream()
                .filter(a -> Boolean.TRUE.equals(a.getActivaParaP2P())).count();
        assertEquals(7, activasAhora);   // 5 sanas + 2 abiertas hasta 7 recibiendo; nunca mas de 8
        assertEquals(false, llenas.get(0).getActivaParaP2P());
    }

    // ── Tomar el control: cerrar las cuentas que sobran ───────────

    private List<AccountCop> activasConSaldos(double... saldos) {
        List<AccountCop> l = new ArrayList<>();
        for (int i = 0; i < saldos.length; i++) l.add(cuenta(i + 1, saldos[i], 10_000, 2_700));
        return l;
    }

    private long cuantasActivas(List<AccountCop> l) {
        return l.stream().filter(c -> Boolean.TRUE.equals(c.getActivaParaP2P())).count();
    }

    @Test
    void sobrantes_conOchoRecibiendo_cierraLaDeMenosEspacio_sinVentas() {
        // Saldos 1.000..8.000: la de 8.000 (id 8) es la de menos espacio libre.
        List<AccountCop> cuentas = activasConSaldos(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 7_000, 8_000);
        when(accountCopRepository.findAll()).thenReturn(cuentas);

        servicio.asignar(List.of());

        assertEquals(false, cuentas.get(7).getActivaParaP2P());
        assertEquals(7, cuantasActivas(cuentas));
    }

    @Test
    void sobrantes_laDeMenosEspacioTieneVentaAbierta_cierraLaSiguienteSinVentas() {
        List<AccountCop> cuentas = activasConSaldos(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 7_000, 8_000);
        when(accountCopRepository.findAll()).thenReturn(cuentas);

        servicio.asignar(List.of(orden("o1", 500, 8)));   // la 8 espera una venta: no se toca

        assertEquals(true, cuentas.get(7).getActivaParaP2P());
        assertEquals(false, cuentas.get(6).getActivaParaP2P()); // la siguiente de menos espacio
    }

    @Test
    void sobrantes_conSieteActivas_noCierraNinguna() {
        List<AccountCop> cuentas = activasConSaldos(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 7_000);
        when(accountCopRepository.findAll()).thenReturn(cuentas);

        servicio.asignar(List.of());

        assertEquals(7, cuantasActivas(cuentas));
    }

    @Test
    void sobrantes_conNueveRecibiendo_cierraDos() {
        List<AccountCop> cuentas = activasConSaldos(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 7_000, 8_000, 9_000);
        when(accountCopRepository.findAll()).thenReturn(cuentas);

        servicio.asignar(List.of());

        assertEquals(7, cuantasActivas(cuentas));
        assertEquals(false, cuentas.get(8).getActivaParaP2P());
        assertEquals(false, cuentas.get(7).getActivaParaP2P());
    }

    @Test
    void sobrantes_siTodasEsperanVentas_noCierraNingunaYNoRevienta() {
        List<AccountCop> cuentas = activasConSaldos(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 7_000, 8_000);
        when(accountCopRepository.findAll()).thenReturn(cuentas);
        List<ActiveP2POrderDto> ventas = new ArrayList<>();
        for (int i = 1; i <= 8; i++) ventas.add(orden("o" + i, 100, i));

        servicio.asignar(ventas);

        assertEquals(8, cuantasActivas(cuentas));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sobrantes_despuesDeCerrar_noReabreOtraCandidata() {
        List<AccountCop> activas = activasConSaldos(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 7_000, 8_000);
        List<AccountCop> cand = candidatas(20, 3);
        when(accountCopRepository.findAll()).thenReturn(juntas(activas, cand));

        servicio.asignar(List.of());

        assertEquals(7, cuantasActivas(activas));
        assertEquals(0, cuantasActivas(cand));
    }

    @Test
    void sobrantes_laRecienPedidaAMovimientosSeCierraUltima() {
        List<AccountCop> cuentas = activasConSaldos(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 7_000, 8_000);
        when(accountCopRepository.findAll()).thenReturn(cuentas);
        lenient().when(cuentasPendientes.estaPendiente(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(cuentasPendientes.estaPendiente("Cuenta 8")).thenReturn(true); // aun sin confirmar: la de menos espacio

        servicio.asignar(List.of());

        assertEquals(true, cuentas.get(7).getActivaParaP2P());
        assertEquals(false, cuentas.get(6).getActivaParaP2P());
    }

    // ── Reemplazo antes de cerrar: siempre 7 trabajando ───────────

    @Test
    @SuppressWarnings("unchecked")
    void reemplazo_laLlenaSeQuedaAbiertaMientrasLaNuevaNoEstaConfirmada() {
        List<AccountCop> grupo = activasConSaldos(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 10_000); // la 7 esta llena
        List<AccountCop> cand = candidatas(20, 2);
        when(accountCopRepository.findAll()).thenReturn(juntas(grupo, cand));
        lenient().when(cuentasPendientes.estaPendiente(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        for (String n : List.of("Cuenta 20", "Cuenta 21")) lenient().when(cuentasPendientes.estaPendiente(n)).thenReturn(true); // la que se abra: sin confirmar

        servicio.asignar(List.of());

        assertEquals(1, cuantasActivas(cand));                // la reposicion se pidio ya
        assertEquals(true, grupo.get(6).getActivaParaP2P());  // la llena sigue abierta (son 8 por un rato)
    }

    @Test
    @SuppressWarnings("unchecked")
    void reemplazo_cuandoLaNuevaSeConfirma_seCierraLaLlena() {
        List<AccountCop> grupo = activasConSaldos(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 10_000);
        AccountCop nueva = cuenta(20, 1_000, 10_000, 2_700); // ya activa y confirmada
        List<AccountCop> todas = new ArrayList<>(grupo);
        todas.add(nueva);
        when(accountCopRepository.findAll()).thenReturn(todas);

        servicio.asignar(List.of());

        assertEquals(false, grupo.get(6).getActivaParaP2P());
        assertEquals(7, cuantasActivas(todas));
    }

    @Test
    void reemplazo_sinCandidatas_laLlenaSeCierraDeInmediato() {
        List<AccountCop> grupo = activasConSaldos(1_000, 2_000, 3_000, 4_000, 5_000, 6_000, 10_000);
        when(accountCopRepository.findAll()).thenReturn(grupo);

        servicio.asignar(List.of());

        assertEquals(false, grupo.get(6).getActivaParaP2P());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reemplazo_noPasaDeLaOchavaCuenta() {
        // Dos llenas a la vez: la reposicion de la segunda espera a que se cierre la primera (tope de 8).
        List<AccountCop> grupo = activasConSaldos(1_000, 2_000, 3_000, 4_000, 5_000, 10_000, 10_000);
        List<AccountCop> cand = candidatas(20, 3);
        when(accountCopRepository.findAll()).thenReturn(juntas(grupo, cand));
        lenient().when(cuentasPendientes.estaPendiente(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        for (String n : List.of("Cuenta 20", "Cuenta 21", "Cuenta 22")) lenient().when(cuentasPendientes.estaPendiente(n)).thenReturn(true);

        servicio.asignar(List.of());

        assertEquals(true, cuantasActivas(juntas(grupo, cand)) <= 8);
    }

    // ── Corresponsal agotado de día: se trabaja por cajero con las mismas reglas ──

    @Test
    @SuppressWarnings("unchecked")
    void unaDeLaTarde_sinCupoDeCorresponsalEnNingunaCuenta_abreCuentasPorCajero() {
        horaDelDia(13, 0);
        // 5 activas y 3 candidatas: ninguna tiene cupo de corresponsal (0), todas tienen cajero.
        List<AccountCop> activas = new ArrayList<>();
        for (int i = 1; i <= 5; i++) activas.add(cuenta(i, 500 * i, 0, 2_700));
        List<AccountCop> cand = new ArrayList<>();
        for (int i = 0; i < 3; i++) cand.add(inactiva2(20 + i, 100 * (i + 1)));
        when(accountCopRepository.findAll()).thenReturn(juntas(activas, cand));

        servicio.asignar(List.of());

        assertEquals(5, cuantasActivas(activas));              // las activas siguen: tienen espacio de cajero
        assertEquals(2, cuantasActivas(cand));                 // completa las 7 buscando por cajero
        verify(cuentaP2PSyncService, org.mockito.Mockito.times(2)).sincronizar(any(), eq(false));
        assertEquals(2, cand.stream().filter(c -> "CAJERO".equals(c.getCupoTipoP2P())).count()); // las nuevas se marcan para cajero
    }

    @Test
    void unaDeLaTarde_sinCupoDeCorresponsal_laVentaSeAsignaPorEspacioDeCajero() {
        horaDelDia(13, 0);
        List<AccountCop> activas = new ArrayList<>();
        for (int i = 1; i <= 7; i++) activas.add(cuenta(i, 1_000, 0, 2_700)); // cajero: 1.700 de espacio
        when(accountCopRepository.findAll()).thenReturn(activas);

        servicio.asignar(List.of(orden("v1", 1_500, null)));

        verify(activeOrderService).upsertPreAsignacion(eq("v1"), any(), any(), any());
    }

    private AccountCop inactiva2(int id, double saldo) {
        AccountCop c = cuenta(id, saldo, 0, 2_700);   // sin corresponsal, con cajero
        c.setActivaParaP2P(false);
        return c;
    }
}
