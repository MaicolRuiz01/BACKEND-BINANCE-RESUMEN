package com.binance.web.model;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SellDollarsDto {
    private Integer id;
    private String idWithdrawals;
    private Double tasa;
    private Double dollars;
    private Double pesos;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "America/Bogota")
    private LocalDateTime date;

    private String nameAccount;
    private Integer accountBinanceId;

    // Esto solo será para cuando haya una orden trade de TRX
    private Double equivalenteciaTRX;
    private Integer supplier;

    private List<AssignAccountDto> accounts;
    private Integer clienteId;
    private List<String> nombresCuentasAsignadas;
    private Double comision;
    private Double networkFeeInSOL;
    // Nuevo: símbolo de la cripto vendida
    private String cryptoSymbol; // Ej: "USDT", "TRX"
    private String tipoCuenta;
    private String txId;
    // Monto real que el cliente vendió (en escala "miles", igual que dollars).
    // Si viene informado al asignar, se usa en vez del monto importado para calcular
    // los pesos y se guarda como dollars. El saldo cripto NO se toca (queda el real).
    private Double montoVerdadero;
}
