package com.binance.web.balanceGeneral;

import java.time.LocalDate;
import java.util.List;

public interface BalanceGeneralService {
	void calcularOBalancear(LocalDate fecha);
    List<BalanceGeneral> listarTodos();
    BalanceGeneral obtenerPorFecha(LocalDate fecha);
    BalanceGeneral calcularHoyYRetornar();
    Double obtenerTotalCajas();
    Double obtenerTotalClientes();

}
