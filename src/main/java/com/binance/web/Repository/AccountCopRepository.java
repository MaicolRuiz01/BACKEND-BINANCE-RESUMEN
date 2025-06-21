package com.binance.web.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.AccountCop;

public interface AccountCopRepository extends JpaRepository<AccountCop, Integer>{
	
	
	AccountCop findByName(String name);
	
}
