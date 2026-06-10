package com.binance.web.dto;

import com.binance.web.Entity.TipoRetiro;
import lombok.Data;

import java.util.List;

@Data
public class SolicitudRetiroRequestDto {

    private Long retiradorId;
    private List<DetalleDto> detalles;

    @Data
    public static class DetalleDto {
        private Integer cuentaCopId;
        private TipoRetiro tipoRetiro;       // CAJERO | CORRESPONSAL | COMPLETO
        private Double montoCajero;          // obligatorio si CAJERO o COMPLETO
        private Double montoCorresponsal;    // obligatorio si CORRESPONSAL o COMPLETO
    }
}
