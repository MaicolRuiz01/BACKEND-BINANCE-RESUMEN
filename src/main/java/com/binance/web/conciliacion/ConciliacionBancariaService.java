package com.binance.web.conciliacion;

public interface ConciliacionBancariaService {

    /**
     * Procesa el lote de resultados que manda el bot de conciliación bancaria:
     * empareja cada fila por nombre contra las cuentas COP de Bancolombia, y
     * guarda disponibilidad + desfase (calculado con el saldo EN VIVO de
     * Pochonance, no el que mandó el bot) + error si aplica.
     */
    ConciliacionResponseDto procesarResultado(ConciliacionResultadoDto request);
}
