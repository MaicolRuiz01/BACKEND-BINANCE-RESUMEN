package com.binance.web.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.VesAverageRate;

public interface VesAverageRateRepository extends JpaRepository<VesAverageRate, Integer> {
	
	Optional<VesAverageRate> findTopByOrderByFechaCalculoDesc();

    List<VesAverageRate> findByDia(LocalDate dia);

}
