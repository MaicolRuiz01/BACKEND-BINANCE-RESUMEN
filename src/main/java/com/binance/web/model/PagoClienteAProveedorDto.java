package com.binance.web.model;

import lombok.Data;

@Data
public class PagoClienteAProveedorDto {
	private Integer clienteOrigenId; 
    private Integer proveedorDestinoId; 
    private Double usdt;             
    private Double tasaCliente;       
    private Double tasaProveedor;      
    private String nota;

}
