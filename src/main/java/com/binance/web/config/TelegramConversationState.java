package com.binance.web.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Mantiene el estado conversacional de cada chat de Telegram.
 * Clave: chatId (Long). Valor: nombre del estado actual (p.ej. "WAITING_ENTREGA_AMOUNT").
 * Es thread-safe gracias a ConcurrentHashMap.
 */
@Component
public class TelegramConversationState {

    private final Map<Long, String> states = new ConcurrentHashMap<>();

    public void set(Long chatId, String state) {
        states.put(chatId, state);
    }

    /** Comprueba si el chatId está exactamente en el estado esperado. */
    public boolean is(Long chatId, String expectedState) {
        return expectedState.equals(states.get(chatId));
    }

    public void clear(Long chatId) {
        states.remove(chatId);
    }
}
