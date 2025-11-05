package com.binance.web.model;

import lombok.Data;

@Data
public class PagoProveedorAClienteDto {
	private Integer proveedorOrigenId;
    private Integer clienteDestinoId;
    private Double usdt;
    private Double tasaProveedor;
    private Double tasaCliente;
    private String nota;
}
