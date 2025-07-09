package com.binance.web.Repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.PurchaseRate;

@Repository
public interface PurchaseRateRepository extends JpaRepository<PurchaseRate, Integer> {
	@Query(value = "SELECT * FROM purchase_rate WHERE DATE(date) = :fecha", nativeQuery = true)
	PurchaseRate findByDateWithoutTime(@Param("fecha") LocalDate fecha);

	// Método para obtener la última tasa de compra ordenada por fecha (más
	// reciente)
	PurchaseRate findTopByOrderByDateDesc();
	Optional<PurchaseRate> findTopOptionalByOrderByDateDesc();
	PurchaseRate findTopByOrderByIdDesc();

}
