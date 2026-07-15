package com.binance.web;

import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.Movimiento;
import com.binance.web.Entity.Retirador;
import com.binance.web.Repository.RetiradorRepository;
import com.binance.web.Repository.SolicitudRetiroRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.movimientos.MovimientoDTO;
import com.binance.web.movimientos.MovimientoService;
import com.binance.web.service.GastoService;
import com.binance.web.service.RetiradorService;
import com.binance.web.service.TelegramService;
import com.binance.web.service.TelegramWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del reporte de "Movimientos de hoy" que ve el retirador en Telegram
 * (TelegramWebhookService.buildReporteMovimientos), a pedido de Milton
 * después de encontrar dos bugs reales al probar en producción:
 *
 * 1. El emparejamiento de CAJERO+CORRESPONSAL de una misma cuenta usaba el
 *    instante EXACTO (con milisegundos) en vez de una ventana de tiempo, así
 *    que dos movimientos creados por separado (aunque en el mismo minuto)
 *    nunca se unificaban. Se corrigió con un emparejamiento por "más cercano
 *    en el tiempo" dentro de una ventana de 15 minutos.
 * 2. El reloj del bloque unificado debe mostrar SIEMPRE las dos horas
 *    ("primero → segundo"), no solo cuando caen en minutos distintos.
 *
 * Como buildReporteMovimientos es privado y no toca ninguna de las
 * dependencias inyectadas (solo arma texto a partir de los parámetros que
 * recibe), se invoca por reflexión — así no hace falta simular todo el flujo
 * de Telegram/BD para probar exclusivamente el armado del reporte.
 */
@ExtendWith(MockitoExtension.class)
public class TelegramReporteMovimientosTest {

    @Mock private RetiradorRepository retiradorRepository;
    @Mock private SolicitudRetiroRepository solicitudRepository;
    @Mock private TelegramService telegramService;
    @Mock private RetiradorService retiradorService;
    @Mock private SupplierRepository supplierRepository;
    @Mock private MovimientoService movimientoService;
    @Mock private GastoService gastoService;

    private TelegramWebhookService webhookService;
    private Retirador retirador;
    private Method buildReporteMovimientos;

    @BeforeEach
    void setUp() throws Exception {
        webhookService = new TelegramWebhookService(retiradorRepository, solicitudRepository,
                telegramService, retiradorService, supplierRepository, movimientoService, gastoService);

        Efectivo caja = new Efectivo();
        caja.setId(1);
        caja.setSaldo(40883.0);

        retirador = new Retirador();
        retirador.setId(1L);
        retirador.setNombre("Pruebas Milton");
        retirador.setEfectivo(caja);

        buildReporteMovimientos = TelegramWebhookService.class.getDeclaredMethod(
                "buildReporteMovimientos", Retirador.class, List.class, List.class);
        buildReporteMovimientos.setAccessible(true);
    }

    private String reporte(List<MovimientoDTO> movimientos) throws Exception {
        return (String) buildReporteMovimientos.invoke(webhookService, retirador, movimientos, Collections.emptyList());
    }

    private MovimientoDTO retiro(int id, String tipo, LocalDateTime fecha, double monto, String cuenta) {
        return new MovimientoDTO(id, tipo, fecha, monto, cuenta, null, null, null, null, null, null);
    }

    private MovimientoDTO retiroConMotivo(int id, String tipo, LocalDateTime fecha, double monto, String cuenta, String motivo) {
        return new MovimientoDTO(id, tipo, fecha, monto, cuenta, null, null, null, null, null, motivo);
    }

    @Test
    void cajeroPrimeroYCorresponsalDespues_unificaYMuestraAmbasHorasEnOrden() throws Exception {
        LocalDateTime horaCajero = LocalDateTime.of(2026, 7, 15, 10, 30);
        LocalDateTime horaCorresponsal = LocalDateTime.of(2026, 7, 15, 10, 35);

        String r = reporte(List.of(
                retiro(1, "RETIRO CAJERO", horaCajero, 2700.0, "David"),
                retiro(2, "RETIRO CORRESPONSAL", horaCorresponsal, 5000.0, "David")
        ));

        assertTrue(r.contains("10:30"), "Debe mostrar la hora del primer movimiento (cajero)");
        assertTrue(r.contains("10:35"), "Debe mostrar la hora del segundo movimiento (corresponsal)");
        assertTrue(r.contains("10:30* → *10:35"), "Debe mostrar el rango en orden cronológico: " + r);
        assertTrue(r.contains("Total — $7.700") || r.contains("Total — $7,700"), "Debe sumar ambos montos: " + r);

        // El CAJERO ocurrió primero → debe aparecer antes que el CORRESPONSAL en el texto.
        int idxCajero = r.indexOf("CAJERO — $2.700") >= 0 ? r.indexOf("CAJERO — $2.700") : r.indexOf("CAJERO — $2,700");
        int idxCorresponsal = r.indexOf("CORRESPONSAL — $5.000") >= 0 ? r.indexOf("CORRESPONSAL — $5.000") : r.indexOf("CORRESPONSAL — $5,000");
        assertTrue(idxCajero >= 0 && idxCorresponsal >= 0, "Ambas líneas deben estar presentes: " + r);
        assertTrue(idxCajero < idxCorresponsal, "CAJERO ocurrió primero, debe listarse primero: " + r);
    }

    @Test
    void corresponsalPrimeroYCajeroDespues_respetaOrdenCronologicoInvertido() throws Exception {
        LocalDateTime horaCorresponsal = LocalDateTime.of(2026, 7, 15, 10, 20);
        LocalDateTime horaCajero = LocalDateTime.of(2026, 7, 15, 10, 25);

        String r = reporte(List.of(
                retiro(1, "RETIRO CORRESPONSAL", horaCorresponsal, 5000.0, "Diana"),
                retiro(2, "RETIRO CAJERO", horaCajero, 2700.0, "Diana")
        ));

        assertTrue(r.contains("10:20* → *10:25"), "El rango debe ir del primero (corresponsal) al segundo (cajero): " + r);

        int idxCorresponsal = r.indexOf("CORRESPONSAL — $5");
        int idxCajero = r.indexOf("CAJERO — $2");
        assertTrue(idxCorresponsal >= 0 && idxCajero >= 0);
        assertTrue(idxCorresponsal < idxCajero,
                "CORRESPONSAL ocurrió primero en este caso, debe listarse primero: " + r);
    }

    @Test
    void mismoMinutoExacto_igualMuestraAmbasHoras() throws Exception {
        LocalDateTime hora = LocalDateTime.of(2026, 7, 15, 10, 34, 0);
        LocalDateTime horaCasiIgual = LocalDateTime.of(2026, 7, 15, 10, 34, 40); // mismo minuto, distinto segundo

        String r = reporte(List.of(
                retiro(1, "RETIRO CORRESPONSAL", hora, 5000.0, "Test"),
                retiro(2, "RETIRO CAJERO", horaCasiIgual, 2700.0, "Test")
        ));

        // Aunque el HH:mm formateado sea igual para ambos, las dos horas deben
        // mostrarse igual (regla explícita de Milton: "siempre las dos horas").
        assertTrue(r.contains("10:34* → *10:34"),
                "Debe mostrar ambas horas aunque caigan en el mismo minuto: " + r);
    }

    @Test
    void masDe15MinutosDeDiferencia_noSeUnifican() throws Exception {
        LocalDateTime horaCajero = LocalDateTime.of(2026, 7, 15, 10, 0);
        LocalDateTime horaCorresponsal = LocalDateTime.of(2026, 7, 15, 10, 20); // 20 min de diferencia

        String r = reporte(List.of(
                retiro(1, "RETIRO CAJERO", horaCajero, 2700.0, "Herney"),
                retiro(2, "RETIRO CORRESPONSAL", horaCorresponsal, 5000.0, "Herney")
        ));

        assertFalse(r.contains("Total — $"), "Con más de 15 minutos de diferencia NO deben unificarse: " + r);
        assertTrue(r.contains("10:00"));
        assertTrue(r.contains("10:20"));
    }

    @Test
    void retiroSinPareja_semuestraSolo() throws Exception {
        String r = reporte(List.of(
                retiro(1, "RETIRO CORRESPONSAL", LocalDateTime.of(2026, 7, 15, 10, 34), 3000.0, "Jorge")
        ));

        assertFalse(r.contains("Total — $"), "Un movimiento sin pareja no debe generar bloque unificado: " + r);
        assertTrue(r.contains("CORRESPONSAL — $3.000") || r.contains("CORRESPONSAL — $3,000"));
        assertTrue(r.contains("Jorge"));
    }

    @Test
    void noMezclaCuentasDistintasAunqueCoincidanEnHora() throws Exception {
        LocalDateTime hora1 = LocalDateTime.of(2026, 7, 15, 10, 34);
        LocalDateTime hora2 = LocalDateTime.of(2026, 7, 15, 10, 34);

        String r = reporte(List.of(
                retiro(1, "RETIRO CAJERO", hora1, 2700.0, "David"),
                retiro(2, "RETIRO CORRESPONSAL", hora2, 5000.0, "Diana") // cuenta distinta
        ));

        assertFalse(r.contains("Total — $"),
                "No debe unificar cajero de una cuenta con corresponsal de OTRA cuenta: " + r);
    }

    @Test
    void motivoDeMontoRealApareceEtiquetadoDentroDelBloqueUnificado() throws Exception {
        LocalDateTime horaCajero = LocalDateTime.of(2026, 7, 15, 8, 0);
        LocalDateTime horaCorresponsal = LocalDateTime.of(2026, 7, 15, 8, 5);

        String r = reporte(List.of(
                retiroConMotivo(1, "RETIRO CAJERO", horaCajero, 1800.0, "Cuenta X",
                        "Solicitado $2.000 — retirado $1.800"),
                retiro(2, "RETIRO CORRESPONSAL", horaCorresponsal, 5000.0, "Cuenta X")
        ));

        assertTrue(r.contains("CAJERO: Solicitado $2.000 — retirado $1.800"),
                "El motivo del cajero debe quedar etiquetado dentro del bloque unificado: " + r);
    }

    @Test
    void totalesDeHoy_sumanAmbosTiposAunqueEstenUnificados() throws Exception {
        LocalDateTime h1 = LocalDateTime.of(2026, 7, 15, 10, 34);
        LocalDateTime h2 = LocalDateTime.of(2026, 7, 15, 10, 34);

        String r = reporte(List.of(
                retiro(1, "RETIRO CAJERO", h1, 2700.0, "Test"),
                retiro(2, "RETIRO CORRESPONSAL", h2, 5000.0, "Test")
        ));

        assertTrue(r.contains("CORRESPONSAL: $5.000") || r.contains("CORRESPONSAL: $5,000"),
                "El resumen de totales debe seguir sumando por tipo, unificado o no: " + r);
        assertTrue(r.contains("CAJERO: $2.700") || r.contains("CAJERO: $2,700"));
    }

    @Test
    void sinMovimientosHoy_muestraMensajeVacio() throws Exception {
        String r = reporte(Collections.<MovimientoDTO>emptyList());
        assertTrue(r.contains("No ha habido movimientos hoy"));
    }
}
