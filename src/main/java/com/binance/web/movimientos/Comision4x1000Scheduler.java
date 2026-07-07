package com.binance.web.movimientos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.Movimiento;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.MovimientoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aplica el 4x1000 DIFERIDO de los retiros de Bancolombia.
 * Al retirar en Bancolombia, ese día solo se descuenta el monto; el 4x1000 queda
 * pendiente (comisionAplicada = false) y este proceso lo descuenta al día siguiente
 * (la cuenta puede quedar en negativo → "saldo por cobrar").
 *
 * Es idempotente y con "catch-up": procesa todo retiro pendiente con fecha ANTERIOR
 * a hoy, así que aunque el backend haya estado apagado, al arrancar se pone al día.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Comision4x1000Scheduler {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final MovimientoRepository movimientoRepository;
    private final AccountCopRepository accountCopRepository;

    /** Cada hora (y al arrancar): aplica el 4x1000 pendiente de días anteriores. */
    @Scheduled(fixedDelayString = "${comision.4x1000.interval-ms:3600000}", initialDelay = 30000)
    @Transactional
    public void aplicarComisionesPendientes() {
        LocalDateTime inicioHoy = LocalDate.now(ZONE).atStartOfDay();
        List<Movimiento> pendientes = movimientoRepository.findByComisionAplicadaFalseAndFechaBefore(inicioHoy);
        if (pendientes.isEmpty()) return;

        int aplicados = 0;
        for (Movimiento m : pendientes) {
            AccountCop cuenta = m.getCuentaOrigen();
            double comision = m.getComision() != null ? m.getComision() : 0.0;
            if (cuenta != null && comision > 0) {
                double saldo = cuenta.getBalance() != null ? cuenta.getBalance() : 0.0;
                // Puede quedar en negativo (saldo por cobrar) — es lo esperado.
                cuenta.setBalance(saldo - comision);
                accountCopRepository.save(cuenta);
            }
            m.setComisionAplicada(true);
            movimientoRepository.save(m);
            aplicados++;
        }
        log.info("[Comision4x1000] {} retiro(s) de Bancolombia con 4x1000 aplicado (diferido).", aplicados);
    }
}
