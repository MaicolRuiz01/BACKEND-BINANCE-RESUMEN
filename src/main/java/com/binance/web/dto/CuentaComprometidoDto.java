package com.binance.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Cuánto dinero de una cuenta COP ya está "comprometido" en solicitudes de
 * retiro enviadas (SIN_ASIGNAR o PENDIENTE) pero todavía no confirmadas por
 * el retirador. El saldo de la cuenta no se descuenta hasta que se confirma,
 * así que esto evita mandar dos solicitudes por el mismo dinero.
 */
@Data
@AllArgsConstructor
public class CuentaComprometidoDto {
    private Integer cuentaCopId;
    private Double montoComprometido;
    private List<SolicitudComprometidaDto> solicitudes;
}
