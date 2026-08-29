package com.binance.web;

import com.binance.web.Entity.AverageRate;
import com.binance.web.Repository.AverageRateRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.TasaPromedioDiagnosticoRepository;
import com.binance.web.service.AccountBinanceService;
import com.binance.web.serviceImpl.AverageRateServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Cálculo de la tasa promedio del USDT: cuánto cuesta, en promedio, cada dólar que tenemos.
 *
 * Fórmula: (USDT que quedaban × tasa vigente + pesos de las compras nuevas)
 *          ÷ (USDT que quedaban + USDT de las compras nuevas)
 *
 * ── ESCALA MILES ──
 * En todo el sistema los montos se guardan divididos por mil, para no leer cifras enormes:
 * una compra de 16.129 USDT se guarda como 16,129. Las TASAS no se dividen (3.155 pesos por
 * USDT son 3.155), porque dividir arriba y abajo por mil deja el mismo cociente.
 *
 * Acá esa conversión está aislada en los dos ayudantes de abajo: las pruebas se escriben con
 * USDT REALES, que es como uno piensa el negocio. Es a propósito — el bug que estas pruebas
 * ahora cubren fue justamente mezclar las dos escalas.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AverageRateCalculoTest {

    private static final double MILES = 1000.0;

    @Mock private AverageRateRepository averageRateRepository;
    @Mock private AccountBinanceService accountBinanceService;
    @Mock private TasaPromedioDiagnosticoRepository diagnosticoRepository;
    @Mock private BuyDollarsRepository buyDollarsRepository;

    @InjectMocks private AverageRateServiceImpl service;

    /** Escenario en USDT REALES: saldo en las cuentas, tasa vigente y otras compras sin asignar. */
    private void escenario(double saldoExternoUsdt, double tasaVigente, double otrosPendientesUsdt) {
        when(accountBinanceService.getTotalExternalUsdt()).thenReturn(saldoExternoUsdt);
        // El repositorio suma la columna amount, que está en escala miles.
        when(buyDollarsRepository.sumAmountPendienteExcluyendo(anyInt()))
                .thenReturn(otrosPendientesUsdt / MILES);

        AverageRate ultima = new AverageRate();
        ultima.setAverageRate(tasaVigente);
        when(averageRateRepository.findTopByOrderByFechaDesc()).thenReturn(Optional.of(ultima));
        when(averageRateRepository.findTopBySesionAbiertaTrueOrderByFechaDesc()).thenReturn(Optional.empty());
        when(averageRateRepository.save(any(AverageRate.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Asigna una compra expresada en USDT REALES (el servicio la recibe en escala miles). */
    private AverageRate asignarCompra(double usdtReales, double tasa, boolean esUltima) {
        return service.actualizarTasaPromedioPorCompra(
                1, LocalDateTime.now(), usdtReales / MILES, tasa, esUltima);
    }

    /** La base que quedó registrada, devuelta en USDT reales. */
    private double baseUsdtReal(AverageRate r) {
        return r.getSaldoInicialDia() * MILES;
    }

    // ── Comportamiento base ───────────────────────────────────

    @Test
    void promediaElInventarioAnteriorConLaCompraNueva() {
        // Hay 1.500 USDT en total y esta compra es de 500 → quedaban 1.000 comprados a 4.000.
        escenario(1500, 4000, 0);

        AverageRate r = asignarCompra(500, 3900, false);

        assertEquals(1000.0, baseUsdtReal(r), 0.01);
        // (1.000 × 4.000 + 500 × 3.900) ÷ 1.500 = 3.966,67
        assertEquals(3966.67, r.getAverageRate(), 0.01);
    }

    @Test
    void comprarMasBaratoBajaLaTasaYNuncaSeSaleDelRango() {
        escenario(1500, 4000, 0);
        AverageRate r = asignarCompra(500, 3900, false);

        // El promedio ponderado SIEMPRE queda entre la tasa de la compra y la de la base.
        assertTrue(r.getAverageRate() < 4000, "comprar más barato debe bajar la tasa");
        assertTrue(r.getAverageRate() > 3900, "no puede quedar por debajo de la compra más barata");
    }

    @Test
    void comprarMasCaroSubeLaTasa() {
        escenario(1500, 4000, 0);
        AverageRate r = asignarCompra(500, 4100, false);

        assertTrue(r.getAverageRate() > 4000);
        assertTrue(r.getAverageRate() < 4100);
    }

    @Test
    void descuentaLasOtrasComprasSinAsignarParaNoContarlasDosVeces() {
        // El saldo de las cuentas YA incluye el USDT de compras pendientes que todavía no tienen
        // tasa real. Si no se restaran, quedarían valoradas a la tasa vieja y después se
        // volverían a sumar con su tasa real: contadas dos veces.
        escenario(2000, 4000, 500);   // 2.000 − 500 (esta) − 500 (otras) = 1.000 de base

        AverageRate r = asignarCompra(500, 3900, false);

        assertEquals(1000.0, baseUsdtReal(r), 0.01);
        assertEquals(3966.67, r.getAverageRate(), 0.01);
    }

    @Test
    void laSesionSeCierraConLaUltimaCompraPendiente() {
        escenario(1500, 4000, 0);

        assertTrue(asignarCompra(500, 3900, false).getSesionAbierta(), "quedan pendientes → sigue abierta");
        assertFalse(asignarCompra(500, 3900, true).getSesionAbierta(), "era la última → se cierra");
    }

    // ── Las dos fallas que se encontraron ─────────────────────

    @Test
    void unaCompraGrandePesaLoQueDebeYNoMilVecesMenos() {
        // ESTE ES EL BUG QUE ROMPÍA TODO.
        //
        // La compra llega en escala miles (16,129) y el saldo de las cuentas venía crudo (4.648).
        // Al restarlos sin convertir, una compra de 16.129 USDT entraba al promedio pesando 16:
        // la base quedaba intacta y enorme, y la tasa se movía centavos por compra. Marcaba
        // 3.064 mientras se compraba a 3.155, y esos 91 pesos de diferencia se reportaban como
        // ganancia en cada venta.
        //
        // Números reales del 29 de agosto de 2026 (fila 5 de tasa_promedio_diagnostico).
        escenario(4648.5351, 3063.747496889063, 600);

        AverageRate r = asignarCompra(16_129, 3155, false);

        // Se compraron 16.129 USDT y en las cuentas quedan 4.648: ya se vendió la mayor parte,
        // así que lo que queda ES de esta compra. No hay inventario viejo que promediar.
        assertEquals(0.0, baseUsdtReal(r), 0.01, "no quedaba inventario anterior");
        assertEquals(3155.0, r.getAverageRate(), 0.01, "la tasa debe ser la que se pagó de verdad");

        // Antes daba 3.064,06: la prueba falla si alguien vuelve a mezclar las escalas.
        assertTrue(r.getAverageRate() > 3100,
                "si da ~3.064 es que la compra volvió a entrar mil veces más chica");
    }

    @Test
    void noUsaElSaldoQueIncluyeLasDemasMonedas() {
        // getTotalExternalBalance convierte SOL, TRX, LINEA y demás a dólares al precio del
        // momento. Si se usara, la tasa promedio del USDT se movería sola cuando se mueve el
        // mercado, sin haber comprado ni vendido nada.
        escenario(1500, 4000, 0);

        asignarCompra(500, 3900, false);

        verify(accountBinanceService, never()).getTotalExternalBalance();
        verify(accountBinanceService, atLeastOnce()).getTotalExternalUsdt();
    }

    // ── Bordes ────────────────────────────────────────────────

    @Test
    void siYaSeVendioLaCompraEnteraLaTasaEsLaDeEsaCompra() {
        // El USDT entra y sale rápido: cuando la compra se asigna tarde, ya se vendió todo y el
        // saldo es menor que la compra. La base se recorta a cero y el promedio pasa a ser la
        // tasa de esta compra, que es lo correcto: es el único USDT del que sabemos el costo.
        escenario(100, 4000, 0);

        AverageRate r = asignarCompra(500, 3900, false);

        assertEquals(0.0, baseUsdtReal(r), 0.01);
        assertEquals(3900.0, r.getAverageRate(), 0.01);
    }

    @Test
    void reproduceLasCuatroComprasRealesDeAgosto() {
        // Las cuatro filas que quedaron guardadas en tasa_promedio_diagnostico el 28 y 29 de
        // agosto de 2026, con los mismos saldos y pendientes que se leyeron ese día.
        // La columna "dio" es lo que el sistema calculó entonces; "debía dar" es lo que se pagó.
        double[][] filas = {
            // saldoUsdt,  tasaBase,             pendientes, compraUsdt, tasaCompra, dio,      debiaDar
            {  437.9651, 3062.2297539828683,          0,      10_354,      3108,   3063.31,   3108 },
            { 8360.6751, 3063.3118153783116,          0,      16_918,      3163,   3063.51,   3163 },
            {10957.9451, 3063.513536493803,      10_879,      17_144,      3155,   3063.66,   3155 },
            { 4648.5351, 3063.747496889063,         600,      16_129,      3155,   3064.06,   3155 },
        };

        for (double[] f : filas) {
            escenario(f[0], f[1], f[2]);
            double tasa = asignarCompra(f[3], f[4], false).getAverageRate();

            assertEquals(f[6], tasa, 0.01,
                    "compra de " + (long) f[3] + " USDT a " + (long) f[4]
                    + ": debía dar " + (long) f[6] + " y el sistema daba " + f[5]);
        }
    }

    @Test
    void registraElDiagnosticoDeCadaCalculo() {
        escenario(1500, 4000, 0);
        asignarCompra(500, 3900, false);

        ArgumentCaptor<com.binance.web.Entity.TasaPromedioDiagnostico> captor =
                ArgumentCaptor.forClass(com.binance.web.Entity.TasaPromedioDiagnostico.class);
        verify(diagnosticoRepository).save(captor.capture());

        // La tabla guarda en escala miles, como el resto del sistema.
        assertEquals(1.5, captor.getValue().getSaldoExternoLeido(), 0.0001);
        assertEquals(1.0, captor.getValue().getSaldoBaseUsdt(), 0.0001);
        assertEquals("APERTURA_SESION", captor.getValue().getEvento());
    }
}
