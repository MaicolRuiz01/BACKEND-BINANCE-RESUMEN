package com.binance.web.service;

import java.time.LocalDate;
import java.util.List;

import com.binance.web.Entity.BalanceGeneral;

public interface BalanceGeneralService {
	void calcularOBalancear(LocalDate fecha);
    List<BalanceGeneral> listarTodos();
    BalanceGeneral obtenerPorFecha(LocalDate fecha);
    BalanceGeneral calcularHoyYRetornar();
    Double obtenerTotalCajas();
    Double obtenerTotalClientes();

    /** Neto (COP) de compras/ventas (dólares + P2P) pendientes por asignar hoy. Cálculo aislado. */
    double calcularNetoNoAsignadoHoy();

}
