package com.binance.web.dto;

import com.binance.web.Entity.TipoRetiro;
import lombok.Data;

import java.util.List;

/**
 * DTO para crear una solicitud de retiro GENERAL (sin retirador asignado de antemano).
 * El retirador se asigna después: vía botón en Telegram o manualmente desde la UI.
 */
@Data
public class SolicitudGeneralRequestDto {

    private List<DetalleDto> detalles;

    @Data
    public static class DetalleDto {
        private Integer cuentaCopId;
        private TipoRetiro tipoRetiro;
        private Double montoCajero;
        private Double montoCorresponsal;
    }
}
