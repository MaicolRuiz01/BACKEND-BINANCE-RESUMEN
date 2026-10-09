package com.binance.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.EstadoSolicitud;
import com.binance.web.Entity.Movimiento;
import com.binance.web.Entity.Retirador;
import com.binance.web.Entity.SolicitudRetiro;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.Repository.RetiradorRepository;
import com.binance.web.Repository.SolicitudRetiroRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.movimientos.MovimientoService;
import com.binance.web.movimientos.MovimientoServiceImplement;
import com.binance.web.service.GastoService;
import com.binance.web.service.RetiradorService;
import com.binance.web.service.TelegramService;
import com.binance.web.service.TelegramUpdateDispatcher;
import com.binance.web.service.TelegramWebhookService;

import jakarta.persistence.EntityManager;

/**
 * Blindaje del flujo de retiros: eliminar un movimiento de retiro y los botones de Telegram del retirador.
 * (Incidente del retiro de Sebastian eliminado el 9/10/2026.)
 */
@ExtendWith(MockitoExtension.class)
class BlindajeRetirosTest {

    // ── Eliminar un movimiento de retiro ──────────────────────────

    @Mock private MovimientoRepository movimientoRepository;
    @Mock private AccountCopRepository accountCopRepository;
    @Mock private EfectivoRepository efectivoRepository;
    @Mock private SolicitudRetiroRepository solicitudRetiroRepository;
    @Mock private TelegramService telegramService;
    @Mock private EntityManager entityManager;
    @Mock private ClienteRepository clienteRepository;
    @Mock private SupplierRepository supplierRepository;
    @InjectMocks private MovimientoServiceImplement movimientos;

    private AccountCop amparo() {
        AccountCop c = new AccountCop();
        c.setId(8);
        c.setName("Amparo");
        c.setBankType(BankType.BANCOLOMBIA);
        c.setBalance(727.0);
        c.setCupoFecha(LocalDate.now(ZoneId.of("America/Bogota")));
        c.setCupoCajeroDisponibleHoy(970.0);          // despues de retirar 1.730 de 2.700
        c.setCupoCorresponsalDisponibleHoy(0.0);
        return c;
    }

    private Movimiento retiroDeAmparo(AccountCop cuenta, Efectivo caja) {
        Movimiento m = new Movimiento();
        m.setId(2910);
        m.setTipo("RETIRO CAJERO");
        m.setMonto(1730.0);
        m.setCuentaOrigen(cuenta);
        m.setCaja(caja);
        m.setFecha(LocalDateTime.now(ZoneId.of("America/Bogota")));
        m.setSolicitudRetiroId(2260L);
        m.setComisionAplicada(false);
        return m;
    }

    private Efectivo caja4() {
        Efectivo e = new Efectivo();
        e.setId(4);
        return e;
    }

    @Test
    void eliminarRetiro_devuelveElSaldoYElCupo_usandoLaCuentaBloqueadaYRefrescada() {
        AccountCop cuenta = amparo();
        Efectivo caja = caja4();
        Movimiento m = retiroDeAmparo(cuenta, caja);
        when(movimientoRepository.findById(2910)).thenReturn(Optional.of(m));
        when(accountCopRepository.findByIdForUpdate(8)).thenReturn(Optional.of(cuenta));
        when(efectivoRepository.findByIdForUpdate(4)).thenReturn(Optional.of(caja));
        SolicitudRetiro solicitud = new SolicitudRetiro();
        solicitud.setEstado(EstadoSolicitud.COMPLETADO);
        when(solicitudRetiroRepository.findById(2260L)).thenReturn(Optional.of(solicitud));
        when(movimientoRepository.findBySolicitudRetiroId(2260L)).thenReturn(Collections.emptyList());

        movimientos.eliminarMovimiento(2910);

        assertEquals(2457.0, cuenta.getBalance(), 0.001);                  // 727 + 1.730
        assertEquals(2700.0, cuenta.getCupoCajeroDisponibleHoy(), 0.001);  // el cupo vuelve
        verify(entityManager).refresh(cuenta);                             // lectura fresca de la fila bloqueada
        verify(efectivoRepository).incrementarSaldo(4, -1730.0);           // y la caja devuelve lo que habia recibido
        verify(movimientoRepository).delete(m);
    }

    @Test
    void eliminarRetiro_siLaSolicitudSeQuedaSinMovimientos_pasaACancelada() {
        AccountCop cuenta = amparo();
        Efectivo caja = caja4();
        Movimiento m = retiroDeAmparo(cuenta, caja);
        when(movimientoRepository.findById(2910)).thenReturn(Optional.of(m));
        when(accountCopRepository.findByIdForUpdate(8)).thenReturn(Optional.of(cuenta));
        when(efectivoRepository.findByIdForUpdate(4)).thenReturn(Optional.of(caja));
        SolicitudRetiro solicitud = new SolicitudRetiro();
        solicitud.setEstado(EstadoSolicitud.COMPLETADO);
        when(solicitudRetiroRepository.findById(2260L)).thenReturn(Optional.of(solicitud));
        when(movimientoRepository.findBySolicitudRetiroId(2260L)).thenReturn(Collections.emptyList());

        movimientos.eliminarMovimiento(2910);

        assertEquals(EstadoSolicitud.CANCELADO, solicitud.getEstado());
        verify(solicitudRetiroRepository).save(solicitud);
    }

    @Test
    void eliminarRetiro_siQuedanOtrosMovimientosDeLaSolicitud_siguesCompletada() {
        AccountCop cuenta = amparo();
        Efectivo caja = caja4();
        Movimiento m = retiroDeAmparo(cuenta, caja);
        when(movimientoRepository.findById(2910)).thenReturn(Optional.of(m));
        when(accountCopRepository.findByIdForUpdate(8)).thenReturn(Optional.of(cuenta));
        when(efectivoRepository.findByIdForUpdate(4)).thenReturn(Optional.of(caja));
        SolicitudRetiro solicitud = new SolicitudRetiro();
        solicitud.setEstado(EstadoSolicitud.COMPLETADO);
        when(solicitudRetiroRepository.findById(2260L)).thenReturn(Optional.of(solicitud));
        when(movimientoRepository.findBySolicitudRetiroId(2260L)).thenReturn(List.of(new Movimiento()));

        movimientos.eliminarMovimiento(2910);

        assertEquals(EstadoSolicitud.COMPLETADO, solicitud.getEstado());   // el otro canal/cuenta del retiro sigue vivo
    }

    @Test
    void eliminarRetiro_unFalloDeTelegramNoTumbaLaReversa() {
        AccountCop cuenta = amparo();
        Efectivo caja = caja4();
        Movimiento m = retiroDeAmparo(cuenta, caja);
        when(movimientoRepository.findById(2910)).thenReturn(Optional.of(m));
        when(accountCopRepository.findByIdForUpdate(8)).thenReturn(Optional.of(cuenta));
        when(efectivoRepository.findByIdForUpdate(4)).thenReturn(Optional.of(caja));
        Retirador retirador = new Retirador();
        retirador.setTelegramChatId(5986612826L);
        SolicitudRetiro solicitud = new SolicitudRetiro();
        solicitud.setEstado(EstadoSolicitud.COMPLETADO);
        solicitud.setRetirador(retirador);
        solicitud.setTelegramPrivateMessageId(77);
        when(solicitudRetiroRepository.findById(2260L)).thenReturn(Optional.of(solicitud));
        when(movimientoRepository.findBySolicitudRetiroId(2260L)).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Telegram caido")).when(telegramService).deleteMessage(anyString(), any());

        movimientos.eliminarMovimiento(2910);   // no debe lanzar

        assertEquals(2457.0, cuenta.getBalance(), 0.001);
        verify(movimientoRepository).delete(m);
    }

    // ── Botones de Telegram del retirador ─────────────────────────

    @Mock private RetiradorRepository retiradorRepository;
    @Mock private RetiradorService retiradorService;
    @Mock private MovimientoService movimientoService;
    @Mock private GastoService gastoService;

    @Test
    void unBotonQueFalla_siempreSeResponde_nuncaSeQuedaCargando() {
        TelegramWebhookService webhook = new TelegramWebhookService(retiradorRepository, solicitudRetiroRepository,
                telegramService, retiradorService, supplierRepository, movimientoService, gastoService,
                clienteRepository, efectivoRepository, accountCopRepository);
        when(retiradorRepository.findByTelegramChatId(any())).thenThrow(new RuntimeException("base de datos caida"));

        Map<String, Object> from = new HashMap<>();
        from.put("id", 123L);
        from.put("username", "sebas");
        Map<String, Object> callback = new HashMap<>();
        callback.put("id", "cb-1");
        callback.put("data", "entregar_start");
        callback.put("from", from);
        Map<String, Object> update = new HashMap<>();
        update.put("callback_query", callback);

        webhook.process(update);   // no debe lanzar

        verify(telegramService).answerCallbackQuery(eq("cb-1"), contains("No se pudo procesar"));
    }

    // ── Despachador: el webhook responde al instante y el orden por usuario se respeta ──

    @Test
    void despachador_procesaEnOrdenPorUsuario_yUnErrorNoDetieneLaCola() throws Exception {
        TelegramUpdateDispatcher d = new TelegramUpdateDispatcher();
        List<Integer> orden = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch fin = new CountDownLatch(3);
        Map<String, Object> update = new HashMap<>();
        Map<String, Object> from = new HashMap<>();
        from.put("id", 42L);
        Map<String, Object> cb = new HashMap<>();
        cb.put("from", from);
        update.put("callback_query", cb);

        d.despachar(update, () -> { orden.add(1); fin.countDown(); });
        d.despachar(update, () -> { fin.countDown(); throw new RuntimeException("boom"); });
        d.despachar(update, () -> { orden.add(3); fin.countDown(); });

        assertTrue(fin.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(1, 3), orden);
    }

    @Test
    void despachador_noBloqueaAlQueLlama() throws Exception {
        TelegramUpdateDispatcher d = new TelegramUpdateDispatcher();
        CountDownLatch liberar = new CountDownLatch(1);
        CountDownLatch termino = new CountDownLatch(1);
        long t0 = System.currentTimeMillis();

        d.despachar(new HashMap<>(), () -> {
            try { liberar.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            termino.countDown();
        });

        assertTrue(System.currentTimeMillis() - t0 < 1_000);   // volvio enseguida aunque la tarea sigue esperando
        liberar.countDown();
        assertTrue(termino.await(5, TimeUnit.SECONDS));
    }
}
