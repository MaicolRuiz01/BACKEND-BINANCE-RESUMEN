package com.binance.web.balance;

import java.util.Date;
import java.util.List;

public interface BalanceService {
	public void createBalance(Date date);
	public List<BalanceDTO> showBalances();
}
