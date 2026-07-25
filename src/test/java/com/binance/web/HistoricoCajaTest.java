package com.binance.web;

import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.Movimiento;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.movimientos.MovimientoServiceImplement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas del histórico de saldo de caja (saldoCajaResultante) pedido por
 * Milton: cada movimiento guarda cuánto quedó la caja justo después de él, y
 * si se edita/elimina un movimiento viejo, el histórico se recalcula SOLO
 * hacia adelante en el tiempo (los movimientos anteriores nunca cambian).
 *
 * El escenario de "editarElMasViejo_propagaElDeltaATodosLosPosteriores"
 * reproduce EXACTO el ejemplo que dio Milton: caja en $30.000, tres retiros
 * de $10.000 (Daniel el más viejo, luego Marcela, luego Diana la más nueva).
 * Al corregir el retiro más viejo (Daniel) de $10.000 a $5.000, los tres
 * quedan en $5.000 / $15.000 / $25.000 — confirmado con Milton que así debe
 * comportarse.
 */
@ExtendWith(MockitoExtension.class)
class HistoricoCajaTest {

    @Mock private MovimientoRepository movimientoRepository;
    @Mock private AccountCopRepository accountCopRepository;
    @Mock private EfectivoRepository efectivoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private SupplierRepository supplierRepository;

    @InjectMocks
    private MovimientoServiceImplement service;

    private Efectivo caja;
    private Movimiento daniel;  // el más viejo
    private Movimiento marcela;
    private Movimiento diana;   // el más nuevo

    @BeforeEach
    void setUp() {
        caja = new Efectivo();
        caja.setId(1);
        caja.setSaldo(30000.0);

        LocalDateTime tDaniel = LocalDateTime.of(2026, 7, 20, 9, 0);
        LocalDateTime tMarcela = LocalDateTime.of(2026, 7, 20, 9, 5);
        LocalDateTime tDiana = LocalDateTime.of(2026, 7, 20, 9, 10);

        daniel = Movimiento.builder().id(1).tipo("RETIRO CAJERO").fecha(tDaniel).monto(10000.0)
                .caja(caja).comisionAplicada(true).saldoCajaResultante(10000.0).build();
        marcela = Movimiento.builder().id(2).tipo("RETIRO CAJERO").fecha(tMarcela).monto(10000.0)
                .caja(caja).comisionAplicada(true).saldoCajaResultante(20000.0).build();
        diana = Movimiento.builder().id(3).tipo("RETIRO CAJERO").fecha(tDiana).monto(10000.0)
                .caja(caja).comisionAplicada(true).saldoCajaResultante(30000.0).build();
    }

    @Test
    void editarElMasViejo_propagaElDeltaATodosLosPosteriores() {
        when(movimientoRepository.findById(1)).thenReturn(Optional.of(daniel));
        when(movimientoRepository.findByCaja_IdAndFechaAfterOrderByFechaAsc(1, daniel.getFecha()))
                .thenReturn(List.of(marcela, diana));

        // Daniel se equivocó: no fueron $10.000, fueron $5.000.
        service.actualizarMovimiento(1, 5000.0, null, null, null);

        assertEquals(5000.0, daniel.getSaldoCajaResultante(), "Daniel (el editado) también se recalcula");
        assertEquals(15000.0, marcela.getSaldoCajaResultante(), "Marcela es posterior a Daniel: se corre el delta");
        assertEquals(25000.0, diana.getSaldoCajaResultante(), "Diana es posterior a Daniel: se corre el delta");
        assertEquals(25000.0, caja.getSaldo(), "el saldo real de la caja también queda cuadrado");
    }

    @Test
    void editarElMasNuevo_nuncaAfectaLosMovimientosAnteriores() {
        when(movimientoRepository.findById(3)).thenReturn(Optional.of(diana));
        when(movimientoRepository.findByCaja_IdAndFechaAfterOrderByFechaAsc(1, diana.getFecha()))
                .thenReturn(List.of()); // no hay nada después de Diana

        // Se corrige el retiro de Diana (el más reciente) de $10.000 a $4.000.
        service.actualizarMovimiento(3, 4000.0, null, null, null);

        assertEquals(10000.0, daniel.getSaldoCajaResultante(), "Daniel es anterior: nunca se toca");
        assertEquals(20000.0, marcela.getSaldoCajaResultante(), "Marcela es anterior: nunca se toca");
        assertEquals(24000.0, diana.getSaldoCajaResultante(), "Diana (la editada) sí se recalcula");
        assertEquals(24000.0, caja.getSaldo());
    }

    @Test
    void propagarDeltaCaja_ignoraMovimientosSinHistoricoTodavia() {
        // Un movimiento viejo, de antes de este cambio, aún sin backfill (null).
        Movimiento sinHistorico = Movimiento.builder().id(9).tipo("RETIRO CAJERO")
                .fecha(LocalDateTime.now()).monto(1000.0).caja(caja).build();

        when(movimientoRepository.findByCaja_IdAndFechaAfterOrderByFechaAsc(eq(1), any()))
                .thenReturn(List.of(sinHistorico));

        service.propagarDeltaCaja(1, LocalDateTime.now().minusMinutes(1), 500.0);

        assertNull(sinHistorico.getSaldoCajaResultante(), "no se debe inventar un valor para un registro sin backfill");
        verify(movimientoRepository, never()).save(sinHistorico);
    }

    @Test
    void recalcularHistoricoCaja_ancladoAlSaldoRealActualDeLaCaja() {
        // Backfill: los 3 movimientos existentes todavía no tienen saldoCajaResultante.
        caja.setSaldo(5000.0); // el saldo real de HOY no tiene por qué coincidir con la suma ingenua
        daniel.setSaldoCajaResultante(null);
        marcela.setSaldoCajaResultante(null);
        diana.setSaldoCajaResultante(null);

        when(efectivoRepository.findById(1)).thenReturn(Optional.of(caja));
        when(movimientoRepository.findByCaja_IdOrCajaDestino_IdOrderByFechaAsc(1, 1))
                .thenReturn(List.of(daniel, marcela, diana));

        service.recalcularHistoricoCaja(1);

        // El último movimiento (Diana) debe cuadrar EXACTO con el saldo real actual.
        assertEquals(-15000.0, daniel.getSaldoCajaResultante());
        assertEquals(-5000.0, marcela.getSaldoCajaResultante());
        assertEquals(5000.0, diana.getSaldoCajaResultante(), "el último debe coincidir con el saldo real de la caja");
    }
}
