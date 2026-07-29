package com.binance.web;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.conciliacion.ConciliacionBancariaServiceImpl;
import com.binance.web.conciliacion.ConciliacionResponseDto;
import com.binance.web.conciliacion.ConciliacionResultadoDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas del endpoint de conciliación bancaria: emparejamiento de cuentas por
 * nombre (con tildes/mayúsculas distintas), cálculo del desfase con el saldo
 * EN VIVO de Pochonance (no el que manda el bot), y manejo de casos raros
 * (nombre que no existe, nombre ambiguo, datos incompletos) sin romper nada.
 */
@ExtendWith(MockitoExtension.class)
class ConciliacionBancariaServiceImplTest {

    @Mock private AccountCopRepository accountCopRepository;

    @InjectMocks
    private ConciliacionBancariaServiceImpl service;

    private AccountCop victor;
    private AccountCop ana;

    @BeforeEach
    void setUp() {
        victor = new AccountCop();
        victor.setId(1);
        victor.setName("Víctor Martínez");
        victor.setBankType(BankType.BANCOLOMBIA);
        victor.setBalance(622.0);

        ana = new AccountCop();
        ana.setId(2);
        ana.setName("Ana");
        ana.setBankType(BankType.BANCOLOMBIA);
        ana.setBalance(1269.0);
    }

    private ConciliacionResultadoDto.Item item(String cuenta, Boolean disponible, Double saldoRealBanco, String error) {
        ConciliacionResultadoDto.Item it = new ConciliacionResultadoDto.Item();
        it.setCuenta(cuenta);
        it.setDisponible(disponible);
        it.setSaldoRealBanco(saldoRealBanco);
        it.setError(error);
        return it;
    }

    private ConciliacionResultadoDto request(ConciliacionResultadoDto.Item... items) {
        ConciliacionResultadoDto dto = new ConciliacionResultadoDto();
        dto.setResultados(List.of(items));
        return dto;
    }

    @Test
    void empareja_ignorandoTildesYMayusculas() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(victor, ana));

        // El bot manda "victor martinez" (sin tilde, minusculas) — debe emparejar con "Víctor Martínez".
        ConciliacionResponseDto resp = service.procesarResultado(
                request(item("victor martinez", true, 615.51, null)));

        assertEquals(1, resp.getActualizados().size());
        assertTrue(resp.getNoEncontrados().isEmpty());
        assertTrue(Boolean.TRUE.equals(victor.getDisponibleBanco()));
        assertNotNull(victor.getUltimaConciliacion());
        verify(accountCopRepository).save(victor);
    }

    @Test
    void calculaDesfaseConElSaldoEnVivoDePochonanceNoConElDelBot() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));
        ana.setBalance(1269.0); // saldo EN VIVO en Pochonance al momento de procesar

        service.procesarResultado(request(item("Ana", true, 1250.0, null)));

        // desfase = saldoRealBanco - balance EN VIVO = 1250 - 1269 = -19
        assertEquals(-19.0, ana.getUltimoDesfaseBanco(), 0.001);
    }

    @Test
    void cuentaNoDisponible_guardaElErrorYDesfaseQuedaNulo() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        ConciliacionResponseDto resp = service.procesarResultado(
                request(item("Ana", false, null, "timeout esperando login")));

        assertEquals(1, resp.getActualizados().size());
        assertFalse(ana.getDisponibleBanco());
        assertNull(ana.getUltimoDesfaseBanco());
        assertEquals("timeout esperando login", ana.getUltimoErrorConciliacion());
    }

    @Test
    void nombreQueNoExisteEnPochonance_seReportaComoNoEncontradoSinRomper() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana, victor));

        ConciliacionResponseDto resp = service.procesarResultado(
                request(item("Cuenta Fantasma", true, 100.0, null)));

        assertTrue(resp.getActualizados().isEmpty());
        assertEquals(List.of("Cuenta Fantasma"), resp.getNoEncontrados());
        verify(accountCopRepository, never()).save(any());
    }

    @Test
    void nombreAmbiguo_dosCuentasConElMismoNombreNormalizado_noActualizaNinguna() {
        AccountCop anaDuplicada = new AccountCop();
        anaDuplicada.setId(3);
        anaDuplicada.setName("ana"); // normaliza igual que "Ana"
        anaDuplicada.setBankType(BankType.BANCOLOMBIA);
        anaDuplicada.setBalance(50.0);

        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana, anaDuplicada));

        ConciliacionResponseDto resp = service.procesarResultado(
                request(item("Ana", true, 100.0, null)));

        assertEquals(List.of("Ana"), resp.getNoEncontrados(),
                "nombre ambiguo: mejor no actualizar ninguna que arriesgarse a la cuenta equivocada");
        verify(accountCopRepository, never()).save(any());
    }

    @Test
    void saldoRealBancoNulo_aunqueDisponibleSeaTrue_desfaseQuedaNuloSinRomper() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        assertDoesNotThrow(() -> service.procesarResultado(
                request(item("Ana", true, null, null))));

        assertNull(ana.getUltimoDesfaseBanco());
    }

    @Test
    void balancePochonanceNulo_cuentaConDatoCorrupto_noRompe() {
        ana.setBalance(null);
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        assertDoesNotThrow(() -> service.procesarResultado(
                request(item("Ana", true, 100.0, null))));

        assertNull(ana.getUltimoDesfaseBanco());
    }

    @Test
    void nombreVacioOEnNulo_noRompeYSeReportaComoNoEncontrado() {
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(List.of(ana));

        ConciliacionResponseDto resp = assertDoesNotThrow(() -> service.procesarResultado(
                request(item(null, true, 100.0, null))));

        assertEquals(1, resp.getNoEncontrados().size());
        verify(accountCopRepository, never()).save(any());
    }

    @Test
    void listaDeResultadosVacia_noRompeYNoLlamaAlRepositorio() {
        ConciliacionResponseDto resp = assertDoesNotThrow(() -> service.procesarResultado(request()));

        assertTrue(resp.getActualizados().isEmpty());
        assertTrue(resp.getNoEncontrados().isEmpty());
        verifyNoInteractions(accountCopRepository);
    }

    @Test
    void requestNulo_noRompeYDevuelveRespuestaVacia() {
        ConciliacionResponseDto resp = assertDoesNotThrow(() -> service.procesarResultado(null));

        assertTrue(resp.getActualizados().isEmpty());
        assertTrue(resp.getNoEncontrados().isEmpty());
        verifyNoInteractions(accountCopRepository);
    }
}
