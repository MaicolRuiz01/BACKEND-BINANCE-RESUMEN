package com.binance.web.conciliacion;

import com.binance.web.util.HttpClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente Telegram del bot de CONCILIACIÓN bancaria (Automatizacion
 * Bancolombia / conciliacion_bancaria.py) — un bot de Telegram DISTINTO al de
 * retiradores (ese usa app.telegram.bot-token vía TelegramService). Este tiene
 * su propio token (app.conciliacion.bot-token) y le habla al chat que el bot
 * mismo registró vía POST /conciliacion/registrar-chat.
 *
 * Aislado en su propia clase (en vez de reusar TelegramServiceImpl) porque
 * ambos bots tienen tokens distintos y no comparten chat — mezclar los dos
 * en un solo servicio con un único @Value de token habría sido un lío.
 */
@Slf4j
@Component
public class ConciliacionBotTelegramClient {

    private final RestTemplate restTemplate = HttpClientFactory.timed();

    @Value("${app.conciliacion.bot-token:}")
    private String botToken;

    /**
     * Best-effort: manda el mensaje y retorna true/false según si lo logró,
     * pero nunca lanza excepción — quien llama (ej. el toggle de "Cuentas
     * P2P") no debe romperse si Telegram falla o el bot no tiene chat
     * registrado todavía.
     */
    public boolean enviarMensaje(Long chatId, String texto) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("[ConciliacionBot] app.conciliacion.bot-token no configurado — no se puede avisar al bot.");
            return false;
        }
        if (chatId == null) {
            log.warn("[ConciliacionBot] Todavía no hay chat_id registrado (el bot no ha llamado a "
                    + "/conciliacion/registrar-chat) — no se puede avisar al bot.");
            return false;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("chat_id", chatId);
            payload.put("text", texto);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
            restTemplate.postForObject(url, new HttpEntity<>(payload, headers), Object.class);
            log.info("[ConciliacionBot] Aviso de conciliación enviado a chat {}", chatId);
            return true;
        } catch (Exception e) {
            log.error("[ConciliacionBot] Error al avisarle al bot de conciliación: {}", e.getMessage());
            return false;
        }
    }
}
