package com.binance.web.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.AccountBinance;

@Repository
public interface AccountBinanceRepository  extends JpaRepository<AccountBinance, Integer>{
	AccountBinance findByName(String name);

	AccountBinance findByUserBinance(String name);

	AccountBinance findByReferenceAccount(String referenceAccount);
	
	AccountBinance findById(int id);  // Este método debe estar presente
	
	List<AccountBinance> findByTipo(String tipo);


}
