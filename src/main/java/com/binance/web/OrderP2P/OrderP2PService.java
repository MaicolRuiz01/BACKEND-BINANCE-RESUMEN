package com.binance.web.OrderP2P;

import java.util.Date;
import java.util.List;

public interface OrderP2PService {

	List<OrderP2PDto> showOrderP2PToday(String account);
	List<OrderP2PDto> showOrderP2PByDateRange(String account, Date fechaInicio, Date fechaFin);
}
