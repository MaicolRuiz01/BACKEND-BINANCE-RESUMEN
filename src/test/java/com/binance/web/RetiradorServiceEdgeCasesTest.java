package com.binance.web;

import com.binance.web.Entity.*;
import com.binance.web.Repository.*;
import com.binance.web.dto.PagoRetiradorDto;
import com.binance.web.dto.SolicitudRetiroRequestDto;
import com.binance.web.service.TelegramService;
import com.binance.web.serviceImpl.RetiradorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Pruebas de "estrés" / datos erróneos sobre el módulo de retiradores
 * (RetiradorServiceImpl), a pedido explícito de Milton: "mándale datos
 * erróneos, equivócate si es necesario, pruébalo para ver si supera los
 * estándares de calidad".
 *
 * Cubre: entradas inválidas en confirmarSolicitudConMontoReal (la feature de
 * "monto real" más reciente), estados inconsistentes de la solicitud, saldo y
 * cupo insuficiente, pagos al retirador con fuente/monto inválidos,
 * cancelaciones fuera de estado, y el guardado que impide borrar un
 * retirador con historial (relevante para el caso real de Camilo).
 *
 * ── HALLAZGO REAL (no arreglado, solo documentado) ────────────────────────
 * El sistema NUNCA valida que los montos de un retiro (montoCajero /
 * montoCorresponsal) sean positivos, ni en crearSolicitud() ni en
 * confirmarSolicitud(). Un monto negativo:
 *  1) En crearSolicitud(): pasa la validación de saldo sin problema, porque
 *     "disponible < detalle.totalDetalle()" nunca es cierto si totalDetalle()
 *     es negativo.
 *  2) En confirmarInterno(): la resta "balance - deduccionHoy" con
 *     deduccionHoy negativo SUMA dinero a la cuenta en vez de restarlo.
 * O sea: con el estado actual del código, cualquier cliente que arme el
 * payload a mano (sin pasar por la UI, que probablemente nunca manda
 * negativos) podría "fabricar" saldo. Las pruebas
 * {@code confirmarSolicitud_montoNegativo_incrementaSaldoEnVezDeRechazar_BUG}
 * y {@code crearSolicitud_montoNegativo_noEsRechazado_BUG} reproducen esto
 * para dejar evidencia. Recomendación: agregar
 * {@code if (montoCajero != null && montoCajero < 0) throw ...} (mismo para
 * corresponsal) tanto en buildDetalles/crearSolicitudGeneral como al
 * principio de confirmarInterno.
 */
@ExtendWith(MockitoExtension.class)
public class RetiradorServiceEdgeCasesTest {

    @Mock
    private RetiradorRepository retiradorRepository;
    @Mock
    private SolicitudRetiroRepository solicitudRepository;
    @Mock
    private AccountCopRepository accountCopRepository;
    @Mock
    private EfectivoRepository efectivoRepository;
    @Mock
    private MovimientoRepository movimientoRepository;
    @Mock
    private TelegramService telegramService;

    private RetiradorServiceImpl retiradorService;

    private AccountCop cuenta;
    private Efectivo caja;
    private Retirador retirador;
    private SolicitudRetiro solicitud;

    @BeforeEach
    void setUp() {
        retiradorService = new RetiradorServiceImpl(retiradorRepository, solicitudRepository,
                accountCopRepository, efectivoRepository, movimientoRepository, telegramService);

        // cupoFecha = hoy para que CupoDiarioRules.asegurarCupoHoy() NO nos resetee los
        // cupos a mitad de la prueba (ver comentario largo en Retiro4x1000Test.setUp()).
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        cuenta = new AccountCop();
        cuenta.setId(1);
        cuenta.setName("Nequi Principal");
        cuenta.setBankType(BankType.NEQUI);
        cuenta.setBalance(20000.0);
        cuenta.setCupoFecha(hoy);
        cuenta.setCupoCajeroDisponibleHoy(50000.0);
        cuenta.setCupoCorresponsalDisponibleHoy(50000.0);

        caja = new Efectivo();
        caja.setId(1);
        caja.setSaldo(0.0);

        retirador = new Retirador();
        retirador.setId(1L);
        retirador.setNombre("Retirador de Prueba");
        retirador.setEfectivo(caja);
        retirador.setSaldoPendiente(1000.0);

        solicitud = new SolicitudRetiro();
        solicitud.setId(1L);
        solicitud.setRetirador(retirador);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        solicitud.setPagoRetirador(2000.0);
    }

    private DetalleRetiro detalleCajero(Double monto) {
        DetalleRetiro d = new DetalleRetiro();
        d.setCuentaCop(cuenta);
        d.setSolicitud(solicitud);
        d.setTipoRetiro(TipoRetiro.CAJERO);
        d.setMontoCajero(monto);
        return d;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // confirmarSolicitudConMontoReal — entradas inválidas
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void montoReal_nulo_lanzaExcepcion() {
        solicitud.setDetalles(Collections.singletonList(detalleCajero(2000.0)));
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        assertThrows(IllegalStateException.class,
                () -> retiradorService.confirmarSolicitudConMontoReal(1L, null));
        assertEquals(EstadoSolicitud.PENDIENTE, solicitud.getEstado());
    }

    @Test
    void montoReal_cero_lanzaExcepcion() {
        solicitud.setDetalles(Collections.singletonList(detalleCajero(2000.0)));
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        assertThrows(IllegalStateException.class,
                () -> retiradorService.confirmarSolicitudConMontoReal(1L, 0.0));
    }

    @Test
    void montoReal_negativo_lanzaExcepcion() {
        solicitud.setDetalles(Collections.singletonList(detalleCajero(2000.0)));
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        assertThrows(IllegalStateException.class,
                () -> retiradorService.confirmarSolicitudConMontoReal(1L, -500.0));
    }

    @Test
    void montoReal_variosDetalles_lanzaExcepcion() {
        DetalleRetiro d1 = detalleCajero(2000.0);
        AccountCop cuenta2 = new AccountCop();
        cuenta2.setId(2);
        cuenta2.setBankType(BankType.NEQUI);
        cuenta2.setBalance(20000.0);
        DetalleRetiro d2 = new DetalleRetiro();
        d2.setCuentaCop(cuenta2);
        d2.setSolicitud(solicitud);
        d2.setTipoRetiro(TipoRetiro.CORRESPONSAL);
        d2.setMontoCorresponsal(3000.0);

        solicitud.setDetalles(List.of(d1, d2));
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> retiradorService.confirmarSolicitudConMontoReal(1L, 1800.0));
        assertTrue(ex.getMessage().toLowerCase().contains("varias cuentas"));
    }

    @Test
    void montoReal_tipoCompleto_lanzaExcepcion() {
        DetalleRetiro d = new DetalleRetiro();
        d.setCuentaCop(cuenta);
        d.setSolicitud(solicitud);
        d.setTipoRetiro(TipoRetiro.COMPLETO);
        d.setMontoCajero(2000.0);
        d.setMontoCorresponsal(3000.0);
        solicitud.setDetalles(Collections.singletonList(d));
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        assertThrows(IllegalStateException.class,
                () -> retiradorService.confirmarSolicitudConMontoReal(1L, 4500.0));
    }

    @Test
    void montoReal_solicitudYaCompletada_lanzaExcepcion() {
        solicitud.setEstado(EstadoSolicitud.COMPLETADO);
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        assertThrows(IllegalStateException.class,
                () -> retiradorService.confirmarSolicitudConMontoReal(1L, 1800.0));
    }

    @Test
    void montoReal_solicitudCancelada_lanzaExcepcion() {
        solicitud.setEstado(EstadoSolicitud.CANCELADO);
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        assertThrows(IllegalStateException.class,
                () -> retiradorService.confirmarSolicitudConMontoReal(1L, 1800.0));
    }

    @Test
    void montoReal_sinRetiradorAsignado_lanzaExcepcion() {
        solicitud.setRetirador(null);
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        assertThrows(IllegalStateException.class,
                () -> retiradorService.confirmarSolicitudConMontoReal(1L, 1800.0));
    }

    @Test
    void montoReal_solicitudInexistente_lanzaExcepcion() {
        when(solicitudRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> retiradorService.confirmarSolicitudConMontoReal(99L, 1800.0));
    }

    @Test
    void montoReal_excedeSaldoDisponible_lanzaExcepcionYNoMutaBalance() {
        cuenta.setBalance(5000.0);
        solicitud.setDetalles(Collections.singletonList(detalleCajero(2000.0)));
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        // Pide registrar que en realidad retiró 6.000, pero la cuenta solo tiene 5.000.
        assertThrows(IllegalStateException.class,
                () -> retiradorService.confirmarSolicitudConMontoReal(1L, 6000.0));
        assertEquals(5000.0, cuenta.getBalance(), "El saldo no debe tocarse si la validación falla");
        assertEquals(EstadoSolicitud.PENDIENTE, solicitud.getEstado());
    }

    @Test
    void montoReal_excedeCupoDiario_lanzaExcepcion() {
        cuenta.setBalance(20000.0);
        cuenta.setCupoCajeroDisponibleHoy(1000.0); // ya casi agotado hoy
        solicitud.setDetalles(Collections.singletonList(detalleCajero(500.0)));
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> retiradorService.confirmarSolicitudConMontoReal(1L, 1500.0));
        assertTrue(ex.getMessage().contains("Cupo diario"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // confirmarSolicitudConMontoReal — casos correctos (comportamiento esperado)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void montoReal_menorAlSolicitado_descuentaMontoRealYAnotaMotivo() {
        cuenta.setBalance(20000.0);
        solicitud.setDetalles(Collections.singletonList(detalleCajero(2000.0)));
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountCopRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SolicitudRetiro resultado = retiradorService.confirmarSolicitudConMontoReal(1L, 1800.0);

        // Se descontó 1.800 (+4x1000 de 1.800 = 7.2), NO 2.000.
        assertEquals(20000.0 - (1800.0 + 1800.0 * 0.004), cuenta.getBalance(), 0.001);
        assertEquals(1800.0, caja.getSaldo());
        assertEquals(EstadoSolicitud.COMPLETADO, resultado.getEstado());
        assertEquals(1800.0, resultado.getTotalMonto());

        ArgumentCaptor<Movimiento> captor = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(captor.capture());
        Movimiento mov = captor.getValue();
        assertNotNull(mov.getMotivo());
        assertTrue(mov.getMotivo().contains("Solicitado $2.000") || mov.getMotivo().contains("Solicitado $2,000"),
                "El motivo debe dejar trazado lo solicitado vs. lo real: " + mov.getMotivo());
    }

    @Test
    void montoReal_igualAlSolicitado_noGeneraMotivo() {
        cuenta.setBalance(20000.0);
        solicitud.setDetalles(Collections.singletonList(detalleCajero(2000.0)));
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountCopRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        retiradorService.confirmarSolicitudConMontoReal(1L, 2000.0);

        ArgumentCaptor<Movimiento> captor = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(captor.capture());
        assertNull(captor.getValue().getMotivo(),
                "Si el monto real es igual al solicitado, no debería generarse motivo de diferencia");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // confirmarSolicitud (flujo normal) — estados y validaciones
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void confirmarSolicitud_inexistente_lanzaExcepcion() {
        when(solicitudRepository.findById(42L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> retiradorService.confirmarSolicitud(42L));
    }

    @Test
    void confirmarSolicitud_cancelada_lanzaExcepcion() {
        solicitud.setEstado(EstadoSolicitud.CANCELADO);
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        assertThrows(IllegalStateException.class, () -> retiradorService.confirmarSolicitud(1L));
    }

    @Test
    void confirmarSolicitud_sinRetirador_lanzaExcepcion() {
        solicitud.setRetirador(null);
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        assertThrows(IllegalStateException.class, () -> retiradorService.confirmarSolicitud(1L));
    }

    @Test
    void confirmarSolicitud_saldoInsuficiente_noMutaBalance() {
        cuenta.setBalance(1000.0);
        solicitud.setDetalles(Collections.singletonList(detalleCajero(5000.0)));
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        assertThrows(IllegalStateException.class, () -> retiradorService.confirmarSolicitud(1L));
        assertEquals(1000.0, cuenta.getBalance());
        assertEquals(0.0, caja.getSaldo());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void confirmarSolicitud_cupoDiarioAgotado_lanzaExcepcion() {
        cuenta.setBalance(20000.0);
        cuenta.setCupoCajeroDisponibleHoy(300.0);
        solicitud.setDetalles(Collections.singletonList(detalleCajero(2000.0)));
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> retiradorService.confirmarSolicitud(1L));
        assertTrue(ex.getMessage().contains("Cupo diario"));
        assertEquals(20000.0, cuenta.getBalance(), "No debe descontarse nada si el cupo no alcanza");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BUG real encontrado: montos negativos no se validan en ningún punto
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void confirmarSolicitud_montoNegativo_incrementaSaldoEnVezDeRechazar_BUG() {
        cuenta.setBalance(20000.0);
        solicitud.setDetalles(Collections.singletonList(detalleCajero(-5000.0)));
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountCopRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // OJO: NO se stubea movimientoRepository.save aquí a propósito — con un monto
        // negativo, "if (montoCajeroUsar > 0)" es falso, así que confirmarInterno NUNCA
        // llega a registrar el Movimiento. Es parte de la evidencia del bug: el dinero
        // "aparece" en el saldo sin dejar ni rastro en el historial.

        retiradorService.confirmarSolicitud(1L);

        // Comportamiento ACTUAL (no deseado): un monto negativo hace que la resta
        // "balance - deduccionHoy" reste un número negativo, es decir, SUME dinero.
        // Lo correcto sería que esto lanzara IllegalArgumentException. Queda
        // documentado aquí como evidencia del hallazgo, no como comportamiento
        // deseable — ver el Javadoc de la clase.
        assertTrue(cuenta.getBalance() > 20000.0,
                "BUG confirmado: un monto negativo incrementa el saldo de la cuenta en vez de ser rechazado (balance quedó en "
                        + cuenta.getBalance() + ")");
    }

    @Test
    void crearSolicitud_montoNegativo_noEsRechazado_BUG() {
        SolicitudRetiroRequestDto request = new SolicitudRetiroRequestDto();
        request.setRetiradorId(1L);
        SolicitudRetiroRequestDto.DetalleDto dto = new SolicitudRetiroRequestDto.DetalleDto();
        dto.setCuentaCopId(1);
        dto.setTipoRetiro(TipoRetiro.CAJERO);
        dto.setMontoCajero(-5000.0);
        request.setDetalles(List.of(dto));

        when(retiradorRepository.findById(1L)).thenReturn(Optional.of(retirador));
        when(accountCopRepository.findById(1)).thenReturn(Optional.of(cuenta));
        when(solicitudRepository.sumComprometidoPorCuenta(anyInt())).thenReturn(0.0);
        when(solicitudRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // BUG confirmado: la validación "disponible < totalDetalle()" nunca dispara
        // para montos negativos, así que la solicitud se crea sin más — debería
        // haber lanzado IllegalArgumentException.
        SolicitudRetiro creada = assertDoesNotThrow(() -> retiradorService.crearSolicitud(request));
        assertEquals(-5000.0, creada.getTotalMonto(),
                "BUG confirmado: se creó una solicitud con monto total negativo");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // pagarRetirador — monto/fuente inválidos
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void pagarRetirador_montoSuperiorASaldoPendiente_lanzaExcepcion() {
        retirador.setSaldoPendiente(1000.0);
        when(retiradorRepository.findById(1L)).thenReturn(Optional.of(retirador));

        PagoRetiradorDto dto = new PagoRetiradorDto();
        dto.setFuente("CAJA");
        dto.setCajaId(1);
        dto.setMonto(5000.0);

        assertThrows(IllegalArgumentException.class, () -> retiradorService.pagarRetirador(1L, dto));
    }

    @Test
    void pagarRetirador_montoNegativo_lanzaExcepcion() {
        when(retiradorRepository.findById(1L)).thenReturn(Optional.of(retirador));

        PagoRetiradorDto dto = new PagoRetiradorDto();
        dto.setFuente("CAJA");
        dto.setMonto(-100.0);

        assertThrows(IllegalArgumentException.class, () -> retiradorService.pagarRetirador(1L, dto));
    }

    @Test
    void pagarRetirador_fuenteInvalida_lanzaExcepcion() {
        retirador.setSaldoPendiente(1000.0);
        when(retiradorRepository.findById(1L)).thenReturn(Optional.of(retirador));

        PagoRetiradorDto dto = new PagoRetiradorDto();
        dto.setFuente("BITCOIN");
        dto.setMonto(500.0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> retiradorService.pagarRetirador(1L, dto));
        assertTrue(ex.getMessage().toLowerCase().contains("fuente"));
    }

    @Test
    void pagarRetirador_cajaSaldoInsuficiente_lanzaExcepcion() {
        retirador.setSaldoPendiente(1000.0);
        caja.setSaldo(100.0);
        when(retiradorRepository.findById(1L)).thenReturn(Optional.of(retirador));
        when(efectivoRepository.findById(1)).thenReturn(Optional.of(caja));

        PagoRetiradorDto dto = new PagoRetiradorDto();
        dto.setFuente("CAJA");
        dto.setCajaId(1);
        dto.setMonto(500.0);

        assertThrows(IllegalStateException.class, () -> retiradorService.pagarRetirador(1L, dto));
        assertEquals(100.0, caja.getSaldo(), "No debe descontarse nada si la caja no alcanza");
    }

    @Test
    void pagarRetirador_cuentaCopSaldoInsuficiente_lanzaExcepcion() {
        retirador.setSaldoPendiente(1000.0);
        cuenta.setBalance(100.0);
        when(retiradorRepository.findById(1L)).thenReturn(Optional.of(retirador));
        when(accountCopRepository.findById(1)).thenReturn(Optional.of(cuenta));

        PagoRetiradorDto dto = new PagoRetiradorDto();
        dto.setFuente("COP");
        dto.setCuentaCopId(1);
        dto.setMonto(500.0);

        assertThrows(IllegalStateException.class, () -> retiradorService.pagarRetirador(1L, dto));
        assertEquals(100.0, cuenta.getBalance());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // cancelarSolicitud — fuera de estado
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void cancelarSolicitud_yaCompletada_lanzaExcepcion() {
        solicitud.setEstado(EstadoSolicitud.COMPLETADO);
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        assertThrows(IllegalStateException.class, () -> retiradorService.cancelarSolicitud(1L));
    }

    @Test
    void cancelarSolicitud_yaCancelada_lanzaExcepcion() {
        solicitud.setEstado(EstadoSolicitud.CANCELADO);
        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));

        assertThrows(IllegalStateException.class, () -> retiradorService.cancelarSolicitud(1L));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // delete(retirador) — eliminación incondicional, preservando historial
    // (caso Camilo: el retirador se puede borrar siempre; la caja queda
    // huérfana intacta, con sus movimientos, y solo se borra aparte)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void eliminarRetirador_desvinculaSolicitudesAbiertasYCerradasPorSeparadoAntesDeBorrar() {
        when(retiradorRepository.findById(1L)).thenReturn(Optional.of(retirador));
        when(solicitudRepository.desvincularSolicitudesAbiertasDeRetirador(1L)).thenReturn(2);
        when(solicitudRepository.desvincularSolicitudesCerradasDeRetirador(1L)).thenReturn(3);

        assertDoesNotThrow(() -> retiradorService.delete(1L));

        // Se usa un UPDATE directo (no cargar+guardar cada SolicitudRetiro en memoria)
        // a propósito, para no disparar el TransientObjectException de Hibernate que
        // salía al guardar objetos justo antes de borrar la fila que referenciaban.
        verify(solicitudRepository).desvincularSolicitudesAbiertasDeRetirador(1L);
        verify(solicitudRepository).desvincularSolicitudesCerradasDeRetirador(1L);
        verify(solicitudRepository, never()).save(any());
        verify(retiradorRepository).delete(retirador);
    }

    @Test
    void eliminarRetirador_noTocaLaCaja() {
        when(retiradorRepository.findById(1L)).thenReturn(Optional.of(retirador));

        retiradorService.delete(1L);

        verifyNoInteractions(efectivoRepository);
    }

    @Test
    void eliminarRetirador_inexistente_lanzaExcepcionYNoDesvinculaNada() {
        when(retiradorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> retiradorService.delete(99L));

        verify(solicitudRepository, never()).desvincularSolicitudesAbiertasDeRetirador(any());
        verify(solicitudRepository, never()).desvincularSolicitudesCerradasDeRetirador(any());
        verify(retiradorRepository, never()).delete(any());
    }
}
