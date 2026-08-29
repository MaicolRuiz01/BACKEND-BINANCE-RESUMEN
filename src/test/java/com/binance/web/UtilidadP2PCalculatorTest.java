package com.binance.web;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SaleP2pAccountCop;
import com.binance.web.Repository.AverageRateRepository;
import com.binance.web.service.UtilidadP2PCalculator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Utilidad de una venta P2P.
 *
 * Estas pruebas existen porque este cálculo estuvo ROTO sin que nadie se enterara: la fórmula
 * vivía en un método que nadie llamaba, así que todas las ventas quedaban con utilidad 0.
 * Un error así no lanza excepciones ni rompe pantallas — solo reporta mal la ganancia.
 *
 * Fórmula: pesos − (dólares + comisión) × tasa promedio de compra − 4x1000 de lo que salió en efectivo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UtilidadP2PCalculatorTest {

    @Mock
    private AverageRateRepository averageRateRepository;

    @InjectMocks
    private UtilidadP2PCalculator calculator;

    // ── Ayudas ────────────────────────────────────────────────

    private SaleP2P venta(double pesos, double dolares, double comision) {
        SaleP2P s = new SaleP2P();
        s.setNumberOrder("ORD-1");
        s.setDate(LocalDateTime.now());
        s.setPesosCop(pesos);
        s.setDollarsUs(dolares);
        s.setCommission(comision);
        s.setAccountCopsDetails(new ArrayList<>());
        return s;
    }

    /** Detalle asignado a una cuenta COP (NO paga 4x1000 en este cálculo). */
    private SaleP2pAccountCop aCuenta(double monto) {
        SaleP2pAccountCop d = new SaleP2pAccountCop();
        d.setAmount(monto);
        d.setAccountCop(new AccountCop());
        return d;
    }

    /** Detalle SIN cuenta COP = salió en efectivo → sí paga 4x1000. */
    private SaleP2pAccountCop aEfectivo(double monto) {
        SaleP2pAccountCop d = new SaleP2pAccountCop();
        d.setAmount(monto);
        d.setAccountCop(null);
        return d;
    }

    private void conTasaPromedio(Double tasa) {
        AverageRate ar = new AverageRate();
        ar.setAverageRate(tasa);
        when(averageRateRepository.findTopByFechaBeforeOrderByFechaDesc(any())).thenReturn(Optional.of(ar));
        when(averageRateRepository.findTopByOrderByFechaDesc()).thenReturn(Optional.of(ar));
    }

    // ── Casos ─────────────────────────────────────────────────

    @Test
    void calculaLaUtilidadConLaFormulaEsperada() {
        // Vendí 100 USDT a 4.100 → 410.000 pesos. Me costaron 4.000 c/u.
        conTasaPromedio(4000.0);
        SaleP2P s = venta(410_000, 100, 0);

        // 410.000 − (100 + 0) × 4.000 = 10.000
        assertEquals(10_000.0, calculator.calcular(s), 0.01);
    }

    @Test
    void laComisionSeSumaAlCosto() {
        // La comisión son USDT que se fueron: cuestan igual que los vendidos.
        conTasaPromedio(4000.0);
        SaleP2P s = venta(410_000, 100, 1);

        // 410.000 − (100 + 1) × 4.000 = 6.000
        assertEquals(6_000.0, calculator.calcular(s), 0.01);
    }

    @Test
    void descuentaEl4x1000SoloDeLoQueSalioEnEfectivo() {
        conTasaPromedio(4000.0);
        SaleP2P s = venta(410_000, 100, 0);
        s.getAccountCopsDetails().add(aCuenta(300_000));    // a cuenta: no paga
        s.getAccountCopsDetails().add(aEfectivo(110_000));  // efectivo: 110.000 × 0.004 = 440

        // 10.000 − 440 = 9.560
        assertEquals(9_560.0, calculator.calcular(s), 0.01);
    }

    @Test
    void vendidoPorDebajoDelCostoDaUtilidadNegativa() {
        // Una pérdida DEBE verse como negativo, no recortarse a cero: si se ocultara,
        // el negocio creería que nunca pierde.
        conTasaPromedio(4200.0);
        SaleP2P s = venta(410_000, 100, 0);

        // 410.000 − 420.000 = −10.000
        assertEquals(-10_000.0, calculator.calcular(s), 0.01);
    }

    @Test
    void sinTasaPromedioDevuelveCeroEnVezDeInventarUnNumero() {
        // Preferible una utilidad visiblemente pendiente que una cifra incorrecta
        // que alguien tome por buena.
        when(averageRateRepository.findTopByFechaBeforeOrderByFechaDesc(any())).thenReturn(Optional.empty());
        when(averageRateRepository.findTopByOrderByFechaDesc()).thenReturn(Optional.empty());

        assertEquals(0.0, calculator.calcular(venta(410_000, 100, 0)), 0.01);
    }

    @Test
    void calcularYAsignarDejaLaUtilidadEnLaVenta() {
        // Este es el paso que faltaba en producción: se calculaba pero no se guardaba.
        conTasaPromedio(4000.0);
        SaleP2P s = venta(410_000, 100, 0);

        calculator.calcularYAsignar(s);

        assertNotNull(s.getUtilidad());
        assertEquals(10_000.0, s.getUtilidad(), 0.01);
    }

    @Test
    void noRevientaConCamposEnNulo() {
        // Las ventas importadas pueden llegar con campos vacíos; el cálculo no debe
        // tumbar la asignación de la venta.
        conTasaPromedio(4000.0);
        SaleP2P s = new SaleP2P();
        s.setDate(LocalDateTime.now());

        assertDoesNotThrow(() -> calculator.calcularYAsignar(s));
        assertNotNull(s.getUtilidad());
    }

    @Test
    void calcularConUsaLaTasaQueSeLePasaYNoLaDelRepositorio() {
        // saveUtilitydefinitive recalcula en bloque con una tasa definitiva.
        SaleP2P s = venta(410_000, 100, 0);

        assertEquals(10_000.0, calculator.calcularCon(s, 4000.0), 0.01);
        assertEquals(0.0, calculator.calcularCon(s, null), 0.01);
        assertEquals(0.0, calculator.calcularCon(s, 0.0), 0.01);
    }
}
