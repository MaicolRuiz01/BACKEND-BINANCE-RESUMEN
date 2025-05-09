package com.binance.web.balance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BalanceRepository extends JpaRepository<Balance, Integer>{
	@Query("SELECT b FROM Balance b ORDER BY b.id DESC")
	Balance findTopByOrderByIdDesc();
}
