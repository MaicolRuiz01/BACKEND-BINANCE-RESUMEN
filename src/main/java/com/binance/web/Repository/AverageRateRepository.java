package com.binance.web.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.AverageRate;

public interface AverageRateRepository extends JpaRepository<AverageRate, Integer> {
	
	Optional<AverageRate> findTopByOrderByFechaDesc();
	Optional<AverageRate> findTopByOrderByIdDesc();

}
