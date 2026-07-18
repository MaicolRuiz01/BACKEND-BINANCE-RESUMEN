package com.binance.web;

import com.binance.web.Entity.EstadoSolicitud;
import com.binance.web.Entity.Retirador;
import com.binance.web.Entity.SolicitudRetiro;
import com.binance.web.Repository.RetiradorRepository;
import com.binance.web.Repository.SolicitudRetiroRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.SupplierRepository;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de los "handlers" del bot de Telegram (TelegramWebhookService),
 * a pedido de Milton después de encontrar y arreglar varios bugs reales en
 * esta sesión (el reporte de movimientos, la eliminación de retiradores).
 *
 * Cubre específicamente el flujo de "monto real" (botones "✅ Retiré todo" /
 * "✏️ Otra cifra" al confirmar un retiro) porque es la lógica más nueva y la
 * que más veces hemos tenido que corregir — no está cubierta por
 * RetiradorServiceEdgeCasesTest (esa prueba el servicio directamente; esta
 * prueba el pegamento de Telegram: parseo de texto, mensajes de error/éxito,
 * y el estado en memoria "pendingMontosReales").
 *
 * Los métodos y el record PendingMontoReal son privados — se invocan por
 * reflexión, igual que en TelegramReporteMovimientosTest.
 */
@ExtendWith(MockitoExtension.class)
public class TelegramWebhookHandlersTest {

    @Mock private RetiradorRepository retiradorRepository;
    @Mock private SolicitudRetiroRepository solicitudRepository;
    @Mock private TelegramService telegramService;
    @Mock private RetiradorService retiradorService;
    @Mock private SupplierRepository supplierRepository;
    @Mock private MovimientoService movimientoService;
    @Mock private GastoService gastoService;
    @Mock private ClienteRepository clienteRepository;

    private TelegramWebhookService webhookService;

    private Method handleCompletedTodo;
    private Method handleCompletedOtra;
    private Method handleMontoRealTexto;
    private Class<?> pendingMontoRealClass;
    private Field pendingMontosRealesField;

    private SolicitudRetiro solicitud;
    private Retirador retirador;

    @BeforeEach
    void setUp() throws Exception {
        webhookService = new TelegramWebhookService(retiradorRepository, solicitudRepository,
                telegramService, retiradorService, supplierRepository, movimientoService, gastoService,
                clienteRepository);

        pendingMontoRealClass = Class.forName("com.binance.web.service.TelegramWebhookService$PendingMontoReal");

        handleCompletedTodo = TelegramWebhookService.class.getDeclaredMethod(
                "handleCompletedTodo", String.class, String.class, Long.class, Integer.class);
        handleCompletedTodo.setAccessible(true);

        handleCompletedOtra = TelegramWebhookService.class.getDeclaredMethod(
                "handleCompletedOtra", String.class, String.class, Long.class, Integer.class);
        handleCompletedOtra.setAccessible(true);

        handleMontoRealTexto = TelegramWebhookService.class.getDeclaredMethod(
                "handleMontoRealTexto", pendingMontoRealClass, Long.class, String.class);
        handleMontoRealTexto.setAccessible(true);

        pendingMontosRealesField = TelegramWebhookService.class.getDeclaredField("pendingMontosReales");
        pendingMontosRealesField.setAccessible(true);

        retirador = new Retirador();
        retirador.setId(1L);
        retirador.setNombre("David");
        retirador.setTelegramChatId(555L);
        com.binance.web.Entity.Efectivo caja = new com.binance.web.Entity.Efectivo();
        caja.setId(1);
        caja.setSaldo(0.0);
        retirador.setEfectivo(caja);

        solicitud = new SolicitudRetiro();
        solicitud.setId(42L);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        solicitud.setTotalMonto(2000.0);
        solicitud.setRetirador(retirador);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Object> pendingMap() throws Exception {
        return (Map<Long, Object>) pendingMontosRealesField.get(webhookService);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // "✅ Retiré todo"
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void completedTodo_solicitudPendiente_confirmaYEditaMensaje() throws Exception {
        when(solicitudRepository.findById(42L)).thenReturn(Optional.of(solicitud));
        when(retiradorRepository.findById(1L)).thenReturn(Optional.of(retirador));

        handleCompletedTodo.invoke(webhookService, "cb1", "completed_todo:42", 555L, 900);

        verify(retiradorService).confirmarSolicitud(42L);
        verify(telegramService).editMessageTextOnly(eq("555"), eq(900), contains("Retiro completado"));
        verify(retiradorService).enviarRecordatorioCaja(retirador);
    }

    @Test
    void completedTodo_solicitudInexistente_avisaYNoConfirma() throws Exception {
        when(solicitudRepository.findById(42L)).thenReturn(Optional.empty());

        handleCompletedTodo.invoke(webhookService, "cb1", "completed_todo:42", 555L, 900);

        verify(retiradorService, never()).confirmarSolicitud(any());
        verify(telegramService).answerCallbackQuery(eq("cb1"), contains("ya no está pendiente"));
        verify(telegramService, never()).editMessageTextOnly(any(), any(), any());
    }

    @Test
    void completedTodo_solicitudYaCompletada_avisaYNoVuelveAConfirmar() throws Exception {
        solicitud.setEstado(EstadoSolicitud.COMPLETADO);
        when(solicitudRepository.findById(42L)).thenReturn(Optional.of(solicitud));

        handleCompletedTodo.invoke(webhookService, "cb1", "completed_todo:42", 555L, 900);

        verify(retiradorService, never()).confirmarSolicitud(any());
    }

    @Test
    void completedTodo_confirmarLanzaExcepcion_avisaErrorYNoEditaMensaje() throws Exception {
        when(solicitudRepository.findById(42L)).thenReturn(Optional.of(solicitud));
        doThrow(new IllegalStateException("Cupo diario de CAJERO agotado"))
                .when(retiradorService).confirmarSolicitud(42L);

        handleCompletedTodo.invoke(webhookService, "cb1", "completed_todo:42", 555L, 900);

        verify(telegramService).answerCallbackQuery(eq("cb1"), contains("Cupo diario de CAJERO agotado"));
        verify(telegramService, never()).editMessageTextOnly(any(), any(), any());
        verify(retiradorService, never()).enviarRecordatorioCaja(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // "✏️ Otra cifra" → pide el monto → handleMontoRealTexto
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void completedOtra_solicitudPendiente_guardaPendingYPideMonto() throws Exception {
        when(solicitudRepository.findById(42L)).thenReturn(Optional.of(solicitud));

        handleCompletedOtra.invoke(webhookService, "cb1", "completed_otra:42", 555L, 900);

        assertTrue(pendingMap().containsKey(555L), "Debe quedar guardado el estado pendiente para ese chat");
        verify(telegramService).editMessageTextOnly(eq("555"), eq(900), eq("Escribe el monto"));
    }

    @Test
    void montoRealTexto_montoValido_confirmaYEnviaResumenYQuitaPending() throws Exception {
        when(solicitudRepository.findById(42L)).thenReturn(Optional.of(solicitud));
        when(retiradorRepository.findById(1L)).thenReturn(Optional.of(retirador));
        handleCompletedOtra.invoke(webhookService, "cb1", "completed_otra:42", 555L, 900);
        Object pending = pendingMap().get(555L);

        handleMontoRealTexto.invoke(webhookService, pending, 555L, "1800");

        verify(retiradorService).confirmarSolicitudConMontoReal(42L, 1800.0);
        verify(telegramService).sendMessage(eq("555"), contains("Retiro registrado"));
        assertFalse(pendingMap().containsKey(555L), "El estado pendiente debe limpiarse tras confirmar con éxito");
    }

    @Test
    void montoRealTexto_conFormatoDePesosYComas_seParseaCorrectamente() throws Exception {
        when(solicitudRepository.findById(42L)).thenReturn(Optional.of(solicitud));
        when(retiradorRepository.findById(1L)).thenReturn(Optional.of(retirador));
        handleCompletedOtra.invoke(webhookService, "cb1", "completed_otra:42", 555L, 900);
        Object pending = pendingMap().get(555L);

        handleMontoRealTexto.invoke(webhookService, pending, 555L, "$9.400");

        verify(retiradorService).confirmarSolicitudConMontoReal(42L, 9400.0);
    }

    @Test
    void montoRealTexto_textoNoNumerico_avisaYMantienePendingParaReintentar() throws Exception {
        when(solicitudRepository.findById(42L)).thenReturn(Optional.of(solicitud));
        handleCompletedOtra.invoke(webhookService, "cb1", "completed_otra:42", 555L, 900);
        Object pending = pendingMap().get(555L);

        handleMontoRealTexto.invoke(webhookService, pending, 555L, "no se cuanto saque");

        verify(retiradorService, never()).confirmarSolicitudConMontoReal(any(), any());
        verify(telegramService).sendMessage(eq("555"), contains("No entendí ese monto"));
        assertTrue(pendingMap().containsKey(555L), "Debe poder reintentar: no se borra el estado pendiente");
    }

    @Test
    void montoRealTexto_montoCeroONegativo_avisaYNoConfirma() throws Exception {
        when(solicitudRepository.findById(42L)).thenReturn(Optional.of(solicitud));
        handleCompletedOtra.invoke(webhookService, "cb1", "completed_otra:42", 555L, 900);
        Object pending = pendingMap().get(555L);

        handleMontoRealTexto.invoke(webhookService, pending, 555L, "0");

        verify(retiradorService, never()).confirmarSolicitudConMontoReal(any(), any());
        verify(telegramService).sendMessage(eq("555"), contains("mayor a $0"));
    }

    @Test
    void montoRealTexto_confirmarLanzaExcepcion_avisaYMantienePendingParaReintentar() throws Exception {
        when(solicitudRepository.findById(42L)).thenReturn(Optional.of(solicitud));
        handleCompletedOtra.invoke(webhookService, "cb1", "completed_otra:42", 555L, 900);
        Object pending = pendingMap().get(555L);

        doThrow(new IllegalStateException("Cupo diario de CORRESPONSAL agotado"))
                .when(retiradorService).confirmarSolicitudConMontoReal(42L, 9999.0);

        handleMontoRealTexto.invoke(webhookService, pending, 555L, "9999");

        verify(telegramService).sendMessage(eq("555"),
                contains("Cupo diario de CORRESPONSAL agotado"));
        assertTrue(pendingMap().containsKey(555L),
                "Si falla la confirmación (ej. cupo agotado) debe poder reintentar con otra cifra");
    }

    @Test
    void montoRealTexto_solicitudYaNoPendiente_avisaYQuitaPending() throws Exception {
        when(solicitudRepository.findById(42L)).thenReturn(Optional.of(solicitud));
        handleCompletedOtra.invoke(webhookService, "cb1", "completed_otra:42", 555L, 900);
        Object pending = pendingMap().get(555L);

        // Mientras el retirador escribía el monto, la solicitud se canceló desde la app.
        solicitud.setEstado(EstadoSolicitud.CANCELADO);

        handleMontoRealTexto.invoke(webhookService, pending, 555L, "1800");

        verify(retiradorService, never()).confirmarSolicitudConMontoReal(any(), any());
        verify(telegramService).sendMessage(eq("555"), contains("ya no está pendiente"));
        assertFalse(pendingMap().containsKey(555L));
    }
}
