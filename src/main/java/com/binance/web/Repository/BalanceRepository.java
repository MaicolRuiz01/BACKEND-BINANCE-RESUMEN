package com.binance.web.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.Balance;

@Repository
public interface BalanceRepository extends JpaRepository<Balance, Integer>{
	@Query("SELECT b FROM Balance b ORDER BY b.id DESC")
	Optional<Balance> findTopByOrderByIdDesc();
}
