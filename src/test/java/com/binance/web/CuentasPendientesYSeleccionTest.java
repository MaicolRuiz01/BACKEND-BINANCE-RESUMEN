package com.binance.web;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.SaleP2PRepository;
import com.binance.web.Repository.SaleP2pAccountCopRepository;
import com.binance.web.activacion.CuentaP2PSyncService;
import com.binance.web.movimientosbridge.MovimientosCuentasPendientes;
import com.binance.web.service.RetiradorService;
import com.binance.web.serviceImpl.AccountCopServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuentasPendientesYSeleccionTest {

    // ── Registro de cuentas pendientes ────────────────────────────

    @Test
    void registro_marcaConfirmaYDescarta() {
        MovimientosCuentasPendientes r = new MovimientosCuentasPendientes();
        assertFalse(r.estaPendiente("Ana Pe\u00f1aranda"));

        r.marcarPendiente("Ana Pe\u00f1aranda ");
        assertTrue(r.estaPendiente("ana penaranda"));      // ignora tildes, mayusculas y espacios

        r.confirmar("Ana Pe\u00f1aranda");
        assertFalse(r.estaPendiente("Ana Pe\u00f1aranda"));

        r.marcarPendiente("Ana Pe\u00f1aranda");
        r.descartar("Ana Pe\u00f1aranda");                  // se desactivo antes de confirmar
        assertFalse(r.estaPendiente("Ana Pe\u00f1aranda"));
    }

    // ── Seleccion de las 5: solo Bancolombia ──────────────────────

    @Mock private AccountCopRepository accountCopRepository;
    @Mock private SaleP2pAccountCopRepository saleP2pAccountCopRepository;
    @Mock private SaleP2PRepository saleP2PRepository;
    @Mock private RetiradorService retiradorService;
    @Mock private CuentaP2PSyncService cuentaP2PSyncService;
    @InjectMocks private AccountCopServiceImpl accountCopService;

    private AccountCop cuenta(int id, BankType banco, double saldo) {
        AccountCop c = new AccountCop();
        c.setId(id);
        c.setName("Cuenta " + id);
        c.setBankType(banco);
        c.setBloqueada(false);
        c.setActivaParaP2P(false);
        c.setBalance(saldo);
        c.setCupoFecha(LocalDate.now(ZoneId.of("America/Bogota")));
        c.setCupoCorresponsalDisponibleHoy(10_000.0);
        c.setCupoCajeroDisponibleHoy(2_700.0);
        return c;
    }

    @Test
    void seleccion_ignoraCuentasQueNoSonBancolombia() {
        AccountCop nequi = cuenta(1, BankType.NEQUI, 100);       // la mas "cercana" si contara
        AccountCop b1 = cuenta(2, BankType.BANCOLOMBIA, 500);
        AccountCop b2 = cuenta(3, BankType.BANCOLOMBIA, 1_000);
        when(accountCopRepository.findAll()).thenReturn(List.of(nequi, b1, b2));

        List<AccountCop> elegidas = accountCopService.activarCincoCuentasMasCercanasAlCupo();

        assertEquals(2, elegidas.size());
        assertTrue(elegidas.stream().allMatch(a -> a.getBankType() == BankType.BANCOLOMBIA));
        assertFalse(Boolean.TRUE.equals(nequi.getActivaParaP2P()));
    }
}
