package com.binance.web.SaleP2P;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.SaleP2P;

@Repository
public interface SaleP2PRepository extends JpaRepository<SaleP2P, Integer>{
	List<SaleP2P> findByBinanceAccount(AccountBinance accountBinance);
	List<SaleP2P> findByDate(Date fecha);
}
