package com.binance.web.model;

import lombok.Data;

@Data
public class AjusteSaldoDto {
	
	private String   entidad;
    private Integer  entidadId;
    private Double   nuevoSaldo;
    private String   motivo;
    private String   actor;

}
