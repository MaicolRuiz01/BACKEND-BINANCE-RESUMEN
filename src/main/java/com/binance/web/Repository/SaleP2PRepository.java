package com.binance.web.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.SaleP2P;

@Repository
public interface SaleP2PRepository extends JpaRepository<SaleP2P, Integer> {
	List<SaleP2P> findByBinanceAccount(AccountBinance accountBinance);

	@Query(value = "SELECT * FROM sale_p2p WHERE DATE(date) = DATE(:fecha)", nativeQuery = true)
	List<SaleP2P> findByDateWithoutTime(@Param("fecha") LocalDate fecha);

	@Query("SELECT s FROM SaleP2P s WHERE DATE(s.date) = :date AND s.binanceAccount.id = :binanceAccountId")
	List<SaleP2P> findByDateAndBinanceAccount(@Param("date") LocalDate date,
			@Param("binanceAccountId") Integer binanceAccountId);

	List<SaleP2P> findByDateBetween(LocalDateTime start, LocalDateTime end);
	boolean existsByNumberOrder(String numberOrder);



}
