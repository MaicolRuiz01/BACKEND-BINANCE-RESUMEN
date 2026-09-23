package com.binance.web.dto;

import lombok.Data;

import java.util.List;

/**
 * Body que manda la Mini App de Telegram ("Solicitar retiro") al crear una
 * solicitud. A diferencia de {@link SolicitudRetiroRequestDto}, no trae
 * retiradorId — el retirador se identifica a partir de initData (firmado por
 * Telegram), nunca de un campo que el cliente podría manipular a mano.
 */
@Data
public class MiniAppSolicitudRetiroDto {
    private String initData;
    private List<SolicitudRetiroRequestDto.DetalleDto> detalles;
}
