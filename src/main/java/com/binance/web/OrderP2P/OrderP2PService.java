package com.binance.web.OrderP2P;

import java.time.LocalDate;
import java.util.List;

public interface OrderP2PService {

	List<OrderP2PDto> showOrderP2PToday(String account, LocalDate date);
	List<OrderP2PDto> showOrderP2PByDateRange(String account, LocalDate fechaInicio, LocalDate fechaFin);
	List<OrderP2PDto> showAllOrderP2(String account);
	List<OrderP2PDto> ultimasOrdenes(int cantidad);
	List<OrderP2PDto> getUltimasOrdenesTodas(int cantidad);

}
