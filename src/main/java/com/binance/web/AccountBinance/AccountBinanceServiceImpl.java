package com.binance.web.AccountBinance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccountBinanceServiceImpl implements AccountBinanceService{

	@Autowired
	private AccountBinanceRepository accountBinanceRepository;
	@Override
	public void saveAccountBinance(AccountBinance accout) {
		accountBinanceRepository.save(accout);
	}
	
	@Override
	public void depositDollars(Double dollars, AccountBinance accout) {
		accout.setBalance(accout.getBalance() + dollars);
		saveAccountBinance(accout);
	}
}
