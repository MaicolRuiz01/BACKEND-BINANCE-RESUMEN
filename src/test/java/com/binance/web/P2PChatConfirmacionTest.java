package com.binance.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.BinanceAPI.P2PChatService;

/** La pantalla solo debe decir "Cuenta enviada" si el mensaje quedó de verdad en el chat de Binance. */
class P2PChatConfirmacionTest {

    private static final String TEXTO = "Oscar Nova\nBANCOLOMBIA\nTipo: Ahorros\nCuenta: 91202498502\nCédula: 13508415";

    private P2PChatService servicio(String respuestaChat) throws Exception {
        BinanceService binance = mock(BinanceService.class);
        when(binance.obtenerMensajesChatCrudos("Luis", "ORD1", 1, 30)).thenReturn(respuestaChat);
        P2PChatService s = new P2PChatService();
        ReflectionTestUtils.setField(s, "binanceService", binance);
        return s;
    }

    private static String mensaje(boolean propio, long createTime, String contenido) {
        return "{\"self\":" + propio + ",\"createTime\":" + createTime + ",\"content\":\""
                + contenido.replace("\n", "\\n") + "\"}";
    }

    @Test
    void mensajePropioConElMismoTexto_seConfirma() throws Exception {
        long ahora = System.currentTimeMillis();
        P2PChatService s = servicio("{\"data\":[" + mensaje(true, ahora, TEXTO) + "]}");
        assertTrue(s.aparecioEnChat("Luis", "ORD1", TEXTO, ahora - 60_000L));
    }

    @Test
    void enElChatNoEstaElMensaje_noSeConfirma() throws Exception {
        long ahora = System.currentTimeMillis();
        P2PChatService s = servicio("{\"data\":[" + mensaje(false, ahora, "ya pague") + "]}");
        assertFalse(s.aparecioEnChat("Luis", "ORD1", TEXTO, ahora - 60_000L));
    }

    @Test
    void elMismoTextoPeroDelCliente_noCuenta() throws Exception {
        long ahora = System.currentTimeMillis();
        P2PChatService s = servicio("{\"data\":[" + mensaje(false, ahora, TEXTO) + "]}");
        assertFalse(s.aparecioEnChat("Luis", "ORD1", TEXTO, ahora - 60_000L));
    }

    @Test
    void unEnvioViejoDeLaMismaCuenta_noCuentaComoElNuevo() throws Exception {
        long ahora = System.currentTimeMillis();
        P2PChatService s = servicio("{\"data\":[" + mensaje(true, ahora - 3_600_000L, TEXTO) + "]}");
        assertFalse(s.aparecioEnChat("Luis", "ORD1", TEXTO, ahora - 60_000L));
    }

    @Test
    void ignoraDiferenciasDeEspaciosYSaltosDeLinea() throws Exception {
        long ahora = System.currentTimeMillis();
        P2PChatService s = servicio("{\"data\":[" + mensaje(true, ahora, TEXTO.replace("\n", " ")) + "]}");
        assertTrue(s.aparecioEnChat("Luis", "ORD1", TEXTO, ahora - 60_000L));
    }
}
