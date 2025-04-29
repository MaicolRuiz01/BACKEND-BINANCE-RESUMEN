package com.binance.web.AccountBinance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountBinanceRepository  extends JpaRepository<AccountBinance, Integer>{
	AccountBinance findByName(String name);

	AccountBinance findByUserBinance(String name);

	AccountBinance findByReferenceAccount(String referenceAccount);
	
	AccountBinance findById(int id);  // Este método debe estar presente

}
