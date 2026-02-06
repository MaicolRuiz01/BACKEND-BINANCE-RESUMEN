package com.binance.web.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.binance.web.Entity.SellDollars;
import com.binance.web.model.SellDollarsDto;

public interface SellDollarsService {
	SellDollars createSellDollars(SellDollarsDto dto);
	List<SellDollars> registrarYObtenerVentasNoAsignadas();
	List<SellDollars> obtenerVentasEntreFechas(LocalDateTime inicio, LocalDateTime fin);
	
	List<SellDollars> obtenerVentasPorFecha(LocalDate fecha);
	List<SellDollars> obtenerVentasPorCliente(Integer clienteId);
	List<SellDollarsDto> listarVentasDto();
	SellDollars updateSellDollars(Integer id, SellDollarsDto dto);
	void registrarVentasAutomaticamente();
	SellDollars asignarVenta(Integer id, SellDollarsDto dto);
	List<SellDollarsDto> ventasPorCliente(Integer clienteId);
	List<SellDollars> obtenerVentasNoAsignadasPorFecha(LocalDate fecha);
	List<SellDollars> obtenerVentasNoAsignadas();

}
