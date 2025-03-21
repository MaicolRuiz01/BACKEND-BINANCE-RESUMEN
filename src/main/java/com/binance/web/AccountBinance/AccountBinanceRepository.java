package com.binance.web.AccountBinance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountBinanceRepository  extends JpaRepository<AccountBinance, Integer>{
	AccountBinance findByName(String name);
}
