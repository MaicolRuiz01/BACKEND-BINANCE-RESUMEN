package com.binance.web.service;

import java.util.List;

import com.binance.web.model.dto.OrderP2PDto;

public interface OrderP2PService {

	List<OrderP2PDto> showOrderP2PToday(String account);
}
