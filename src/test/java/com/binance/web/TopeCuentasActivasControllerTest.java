package com.binance.web;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.Repository.BrebeKeyRepository;
import com.binance.web.activacion.CuentaP2PSyncService;
import com.binance.web.conciliacion.ConciliacionBancariaService;
import com.binance.web.controller.AccountCopController;
import com.binance.web.service.AccountCopExcelService;
import com.binance.web.service.AccountCopService;
import com.binance.web.service.RetiradorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regla de hierro: activar a mano una cuenta Bancolombia nunca pasa de 8 activas. */
@ExtendWith(MockitoExtension.class)
class TopeCuentasActivasControllerTest {

    @Mock private AccountCopService accountCopService;
    @Mock private AccountCopExcelService accountCopExcelService;
    @Mock private BrebeKeyRepository brebeKeyRepository;
    @Mock private RetiradorService retiradorService;
    @Mock private AccountCopRepository accountCopRepository;
    @Mock private MovimientoRepository movimientoRepository;
    @Mock private ConciliacionBancariaService conciliacionBancariaService;
    @Mock private CuentaP2PSyncService cuentaP2PSyncService;
    @InjectMocks private AccountCopController controller;

    private AccountCop cuenta(int id, BankType banco, boolean activa) {
        AccountCop c = new AccountCop();
        c.setId(id);
        c.setName("Cuenta " + id);
        c.setBankType(banco);
        c.setActivaParaP2P(activa);
        c.setBloqueada(false);
        return c;
    }

    private List<AccountCop> activas(int n) {
        List<AccountCop> l = new ArrayList<>();
        for (int i = 1; i <= n; i++) l.add(cuenta(i, BankType.BANCOLOMBIA, true));
        return l;
    }

    @Test
    void conOchoActivas_noDejaActivarUnaBancolombiaMas() {
        AccountCop nueva = cuenta(30, BankType.BANCOLOMBIA, false);
        when(accountCopService.findByIdAccountCop(30)).thenReturn(nueva);
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(activas(8));

        ResponseEntity<?> r = controller.toggleActivaParaP2P(30);

        assertEquals(409, r.getStatusCode().value());
        assertEquals(false, nueva.getActivaParaP2P());
        verify(accountCopService, never()).updateAccountCop(any(), any());
        verify(cuentaP2PSyncService, never()).sincronizar(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void conSieteActivas_siDejaActivarLaOctava() {
        AccountCop nueva = cuenta(30, BankType.BANCOLOMBIA, false);
        when(accountCopService.findByIdAccountCop(30)).thenReturn(nueva);
        when(accountCopRepository.findByBankType(BankType.BANCOLOMBIA)).thenReturn(activas(7));

        ResponseEntity<?> r = controller.toggleActivaParaP2P(30);

        assertEquals(200, r.getStatusCode().value());
        assertEquals(true, nueva.getActivaParaP2P());
        verify(cuentaP2PSyncService).sincronizar(nueva, false);
    }

    @Test
    void desactivar_conOchoActivas_siempreSePermite() {
        AccountCop activa = cuenta(1, BankType.BANCOLOMBIA, true);
        when(accountCopService.findByIdAccountCop(1)).thenReturn(activa);

        ResponseEntity<?> r = controller.toggleActivaParaP2P(1);

        assertEquals(200, r.getStatusCode().value());
        assertEquals(false, activa.getActivaParaP2P());
    }

    @Test
    void unaNequi_noEstaSujetaAlTope_porqueMovimientosNoLaMonitorea() {
        AccountCop nequi = cuenta(40, BankType.NEQUI, false);
        when(accountCopService.findByIdAccountCop(40)).thenReturn(nequi);

        ResponseEntity<?> r = controller.toggleActivaParaP2P(40);

        assertEquals(200, r.getStatusCode().value());
        assertEquals(true, nequi.getActivaParaP2P());
    }
}
