package com.binance.web.dto;

import lombok.Data;

/**
 * DTO para asignar un retirador a una solicitud general.
 * Usado tanto por la UI (manual) como por n8n (callback de Telegram).
 */
@Data
public class AsignarRetiradorDto {
    /** ID del retirador a asignar */
    private Long retiradorId;

    /**
     * Username de Telegram del retirador que presionó el botón.
     * Opcional: cuando viene de n8n permite validar que el username coincide.
     */
    private String telegramUsername;
}
