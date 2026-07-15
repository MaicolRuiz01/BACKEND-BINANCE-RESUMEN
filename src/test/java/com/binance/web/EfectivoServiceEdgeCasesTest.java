package com.binance.web;

import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.Retirador;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.serviceImpl.EfectivoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A pedido de Milton: el retirador se puede eliminar siempre (ver
 * RetiradorServiceEdgeCasesTest), pero la CAJA solo se puede eliminar si ya
 * no tiene ningún retirador vinculado. Estas pruebas cubren esa regla nueva
 * en EfectivoServiceImpl.eliminarCaja.
 */
@ExtendWith(MockitoExtension.class)
public class EfectivoServiceEdgeCasesTest {

    @Mock
    private EfectivoRepository efectivoRepo;

    @InjectMocks
    private EfectivoServiceImpl efectivoService;

    private Efectivo caja;

    @BeforeEach
    void setUp() {
        caja = new Efectivo();
        caja.setId(1);
        caja.setSaldo(0.0);
    }

    @Test
    void eliminarCaja_conRetiradorVinculado_lanzaExcepcionYNoBorra() {
        Retirador retirador = new Retirador();
        retirador.setId(9L);
        retirador.setNombre("Camilo");
        caja.setRetirador(retirador);

        when(efectivoRepo.findById(1)).thenReturn(Optional.of(caja));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> efectivoService.eliminarCaja(1));
        assertTrue(ex.getMessage().contains("Camilo"), "El mensaje debe identificar al retirador vinculado: " + ex.getMessage());
        verify(efectivoRepo, never()).deleteById(any());
    }

    @Test
    void eliminarCaja_sinRetiradorVinculado_seEliminaSinProblema() {
        caja.setRetirador(null); // huérfana, ej. después de borrar su retirador

        when(efectivoRepo.findById(1)).thenReturn(Optional.of(caja));

        assertDoesNotThrow(() -> efectivoService.eliminarCaja(1));
        verify(efectivoRepo).deleteById(1);
    }

    @Test
    void eliminarCaja_inexistente_lanzaExcepcion() {
        when(efectivoRepo.findById(99)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> efectivoService.eliminarCaja(99));
        verify(efectivoRepo, never()).deleteById(any());
    }
}
