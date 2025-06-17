package com.binance.web.balance;

import java.time.LocalDate;
import java.util.List;

public interface BalanceService {
	public void createBalance(LocalDate date);

	List<BalanceDTO> showBalances();

	BalanceDTO showLiveBalanceToday();
}
