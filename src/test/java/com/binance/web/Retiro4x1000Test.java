package com.binance.web;

import com.binance.web.Entity.*;
import com.binance.web.Repository.*;
import com.binance.web.dto.SolicitudRetiroRequestDto;
import com.binance.web.service.TelegramService;
import com.binance.web.serviceImpl.RetiradorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class Retiro4x1000Test {

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

    @InjectMocks
    private RetiradorServiceImpl retiradorService;

    private AccountCop cuentaNoBanco;
    private AccountCop cuentaBancolombia;
    private Retirador retirador;
    private SolicitudRetiro solicitud;

    @BeforeEach
    void setUp() {
        // OJO: CupoDiarioRules.asegurarCupoHoy() RESETEA cupoCajero/CorresponsalDisponibleHoy
        // a los máximos reales del banco (ej. Nequi: $2.700 cajero / $5.000 corresponsal)
        // cada vez que se llama, A MENOS que cupoFecha ya sea la fecha de HOY. Si no fijamos
        // cupoFecha=hoy aquí, cualquier valor "grande" que pongamos en cupoCajero/CorresponsalDisponibleHoy
        // (como el 50.000 de abajo) se pisa silenciosamente con el máximo real del banco tan
        // pronto como confirmarInterno llama asegurarCupoHoy() — lo que rompía, sin que nadie
        // se diera cuenta (nunca se corrieron estas pruebas), los tests de $10.000 que asumían
        // cupo disponible ilimitado.
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        cuentaNoBanco = new AccountCop();
        cuentaNoBanco.setId(1);
        cuentaNoBanco.setName("Nequi");
        cuentaNoBanco.setBankType(com.binance.web.Entity.BankType.NEQUI);
        cuentaNoBanco.setBalance(20000.0);
        cuentaNoBanco.setCupoFecha(hoy);
        cuentaNoBanco.setCupoCajeroDisponibleHoy(50000.0);
        cuentaNoBanco.setCupoCorresponsalDisponibleHoy(50000.0);

        cuentaBancolombia = new AccountCop();
        cuentaBancolombia.setId(2);
        cuentaBancolombia.setName("Bancolombia");
        cuentaBancolombia.setBankType(com.binance.web.Entity.BankType.BANCOLOMBIA);
        cuentaBancolombia.setBalance(20000.0);
        cuentaBancolombia.setCupoFecha(hoy);
        cuentaBancolombia.setCupoCajeroDisponibleHoy(50000.0);
        cuentaBancolombia.setCupoCorresponsalDisponibleHoy(50000.0);

        Efectivo caja = new Efectivo();
        caja.setId(1);
        caja.setSaldo(0.0);

        retirador = new Retirador();
        retirador.setId(1L);
        retirador.setNombre("Test Retirador");
        retirador.setEfectivo(caja);
        retirador.setSaldoPendiente(0.0);

        solicitud = new SolicitudRetiro();
        solicitud.setId(1L);
        solicitud.setRetirador(retirador);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        solicitud.setPagoRetirador(2000.0);
    }

    @Test
    void testRetiroNoBancolombiaAplica4x1000() {
        // Arrange
        DetalleRetiro detalle = new DetalleRetiro();
        detalle.setCuentaCop(cuentaNoBanco);
        detalle.setSolicitud(solicitud);
        detalle.setTipoRetiro(TipoRetiro.CAJERO);
        detalle.setMontoCajero(2000.0);
        
        solicitud.setDetalles(Collections.singletonList(detalle));

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountCopRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        retiradorService.confirmarSolicitud(1L);

        // Assert
        // Monto = 2000
        // 4x1000 = 8
        // Expected deduccion = 2008
        // Original balance = 20000
        // Expected final balance = 20000 - 2008 = 17992
        assertEquals(17992.0, cuentaNoBanco.getBalance(), "El saldo final debe tener el 4x1000 descontado");
    }

    @Test
    void testRetiroBancolombiaNoAplica4x1000() {
        // Arrange
        DetalleRetiro detalle = new DetalleRetiro();
        detalle.setCuentaCop(cuentaBancolombia);
        detalle.setSolicitud(solicitud);
        detalle.setTipoRetiro(TipoRetiro.CAJERO);
        detalle.setMontoCajero(2000.0);
        
        solicitud.setDetalles(Collections.singletonList(detalle));

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountCopRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        retiradorService.confirmarSolicitud(1L);

        // Assert
        // Monto = 2000
        // Bancolombia no debe descontar 4x1000 en el momento
        // Original balance = 20000
        // Expected final balance = 20000 - 2000 = 18000
        assertEquals(18000.0, cuentaBancolombia.getBalance(), "El saldo final NO debe tener el 4x1000 descontado");
    }

    /**
     * Reproduce el caso puntual reportado: retiro de $10.000 por CORRESPONSAL
     * en una cuenta que NO es Bancolombia (el 4x1000 se cobra de una vez al
     * confirmar). Debe descontarse el 4x1000 UNA sola vez ($40), nunca dos.
     */
    @Test
    void testRetiroCorresponsal10000NoBancolombiaAplica4x1000UnaSolaVez() {
        cuentaNoBanco.setBalance(50000.0);

        DetalleRetiro detalle = new DetalleRetiro();
        detalle.setCuentaCop(cuentaNoBanco);
        detalle.setSolicitud(solicitud);
        detalle.setTipoRetiro(TipoRetiro.CORRESPONSAL);
        detalle.setMontoCorresponsal(10000.0);

        solicitud.setDetalles(Collections.singletonList(detalle));

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountCopRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        retiradorService.confirmarSolicitud(1L);

        // Monto = 10.000, 4x1000 = 40, deduccion total = 10.040
        assertEquals(39960.0, cuentaNoBanco.getBalance(),
                "El 4x1000 de un retiro de 10.000 debe ser exactamente 40, descontado una sola vez");
    }

    /**
     * Guardia contra doble clic / doble confirmación de la MISMA solicitud:
     * una vez COMPLETADA, un segundo intento de confirmar debe fallar y NO
     * debe volver a tocar el saldo de la cuenta.
     */
    @Test
    void testNoSePuedeConfirmarDosVecesLaMismaSolicitud() {
        cuentaNoBanco.setBalance(50000.0);

        DetalleRetiro detalle = new DetalleRetiro();
        detalle.setCuentaCop(cuentaNoBanco);
        detalle.setSolicitud(solicitud);
        detalle.setTipoRetiro(TipoRetiro.CORRESPONSAL);
        detalle.setMontoCorresponsal(10000.0);

        solicitud.setDetalles(Collections.singletonList(detalle));

        when(solicitudRepository.findById(1L)).thenReturn(Optional.of(solicitud));
        when(solicitudRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountCopRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        retiradorService.confirmarSolicitud(1L); // primera vez: OK, queda COMPLETADO

        assertThrows(IllegalStateException.class, () -> retiradorService.confirmarSolicitud(1L),
                "Confirmar una solicitud ya COMPLETADA debe lanzar excepción, nunca volver a descontar");

        // El saldo debe reflejar SOLO la primera confirmación (10.040), no dos.
        assertEquals(39960.0, cuentaNoBanco.getBalance(),
                "El segundo intento no debe haber tocado el saldo otra vez");
    }
}
