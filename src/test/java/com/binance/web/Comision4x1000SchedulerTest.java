package com.binance.web;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Entity.Movimiento;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.movimientos.Comision4x1000Scheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pruebas para el scheduler que aplica el 4x1000 DIFERIDO de Bancolombia
 * (Comision4x1000Scheduler), a raíz del reporte de una asesora: "al
 * solicitar un retiro de 10.000, dos veces se descontó el 4x1000".
 *
 * Conclusión del análisis:
 * 1. Un solo retiro (una sola solicitud, un solo Movimiento) NUNCA debería
 *    poder cobrar el 4x1000 dos veces en condiciones normales — el flag
 *    `comisionAplicada` en el Movimiento sirve exactamente para eso: una vez
 *    en true, el scheduler ya no debería volver a tocarlo.
 * 2. PERO el scheduler NO usa bloqueo pesimista (`findByIdForUpdate`, que sí
 *    se usa en otras partes del código, ej. MovimientoServiceImplement línea
 *    135) al tocar el balance de la cuenta. Si el scheduler llegara a
 *    ejecutarse dos veces de forma solapada (dos instancias del backend
 *    corriendo a la vez durante un despliegue, por ejemplo) ANTES de que la
 *    primera ejecución guarde `comisionAplicada = true`, ambas ejecuciones
 *    verían el mismo Movimiento como "pendiente" y descontarían el 4x1000
 *    dos veces. El test `testDobleEjecucionConcurrenteDuplicaElDescuento`
 *    reproduce exactamente ese escenario para dejar demostrado el mecanismo.
 */
@ExtendWith(MockitoExtension.class)
public class Comision4x1000SchedulerTest {

    @Mock
    private MovimientoRepository movimientoRepository;
    @Mock
    private AccountCopRepository accountCopRepository;

    private Comision4x1000Scheduler scheduler;
    private AccountCop cuentaBancolombia;

    @BeforeEach
    void setUp() {
        scheduler = new Comision4x1000Scheduler(movimientoRepository, accountCopRepository);

        cuentaBancolombia = new AccountCop();
        cuentaBancolombia.setId(2);
        cuentaBancolombia.setName("Bancolombia");
        cuentaBancolombia.setBankType(BankType.BANCOLOMBIA);
        cuentaBancolombia.setBalance(20000.0);
    }

    private Movimiento pendiente(double monto) {
        return Movimiento.builder()
                .id(100)
                .tipo("RETIRO CORRESPONSAL")
                .fecha(LocalDateTime.now().minusDays(1))
                .monto(monto)
                .cuentaOrigen(cuentaBancolombia)
                .comision(monto * 0.004)
                .comisionAplicada(false)
                .build();
    }

    @Test
    void testAplicaComisionUnaSolaVezYMarcaComoAplicada() {
        Movimiento mov = pendiente(10000.0);
        when(movimientoRepository.findByComisionAplicadaFalseAndFechaBefore(any()))
                .thenReturn(List.of(mov));
        when(accountCopRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scheduler.aplicarComisionesPendientes();

        // Monto 10.000 -> 4x1000 = 40
        assertEquals(19960.0, cuentaBancolombia.getBalance(),
                "El 4x1000 (40) debe descontarse UNA sola vez del saldo de la cuenta");
        assertEquals(true, mov.getComisionAplicada(), "El movimiento debe quedar marcado como aplicado");
    }

    @Test
    void testNoVuelveATocarUnMovimientoYaAplicado() {
        // Simula lo que hace la query real: un movimiento con comisionAplicada=true
        // ya NO debería venir en la lista de pendientes.
        when(movimientoRepository.findByComisionAplicadaFalseAndFechaBefore(any()))
                .thenReturn(Collections.emptyList());

        scheduler.aplicarComisionesPendientes();

        assertEquals(20000.0, cuentaBancolombia.getBalance(),
                "Si no hay pendientes, el saldo no debe tocarse");
    }

    @Test
    void testDobleEjecucionConcurrenteDuplicaElDescuento() {
        // Escenario de carrera: DOS ejecuciones del scheduler (ej. dos instancias
        // del backend vivas a la vez durante un deploy) leen el MISMO movimiento
        // pendiente ANTES de que ninguna de las dos haya guardado
        // comisionAplicada=true todavía. Esto es exactamente lo que pasaría si el
        // scheduler no tuviera el flag como única defensa y corriera duplicado.
        Movimiento movVistoPorInstanciaA = pendiente(10000.0);
        Movimiento movVistoPorInstanciaB = pendiente(10000.0); // misma cuenta, mismo monto, "vista" aparte

        when(movimientoRepository.findByComisionAplicadaFalseAndFechaBefore(any()))
                .thenReturn(List.of(movVistoPorInstanciaA))
                .thenReturn(List.of(movVistoPorInstanciaB));
        when(accountCopRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Instancia A ejecuta el scheduler
        scheduler.aplicarComisionesPendientes();
        // Instancia B ejecuta el scheduler con su propia copia del mismo movimiento,
        // leída ANTES de que A hubiera guardado comisionAplicada=true
        scheduler.aplicarComisionesPendientes();

        // Si el código tuviera un guardado atómico (UPDATE ... WHERE comisionAplicada=false)
        // o bloqueo pesimista, esto debería quedar en 19960.0 (un solo descuento).
        // Con el código actual, queda en 19920.0: el 4x1000 se descontó DOS veces.
        assertEquals(19920.0, cuentaBancolombia.getBalance(),
                "BUG reproducido: sin bloqueo, dos ejecuciones solapadas descuentan el 4x1000 dos veces");
    }
}
