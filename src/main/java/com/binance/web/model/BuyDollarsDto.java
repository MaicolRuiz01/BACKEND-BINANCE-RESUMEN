package com.binance.web.model;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuyDollarsDto {
	private Integer id;
	private Double amount;
	private String cryptoSymbol;
	private Double tasa;         // Tasa de cambio aplicada
    private String nameAccount;  // Nombre de la cuenta
    //@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "America/Bogota")
    private LocalDateTime date;           // Fecha de la operación
    private String idDeposit;
    private Double pesos;
    private Integer accountBinanceId;
	private Integer supplierId;
    private Integer clienteId;
	private Boolean asignada;
	private String txId;
	// Monto real que el cliente compró (en escala "miles", igual que amount).
	// Si viene informado al asignar, se usa en vez del monto importado para calcular
	// los pesos y se guarda como amount. El saldo cripto NO se toca (queda el real recibido).
	private Double montoVerdadero;
}
