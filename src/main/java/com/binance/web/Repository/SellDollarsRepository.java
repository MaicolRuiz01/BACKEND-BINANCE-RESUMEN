package com.binance.web.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.Supplier;

public interface SellDollarsRepository extends JpaRepository<SellDollars, Integer> {
	@Query(value = "SELECT * FROM sell_dollars WHERE DATE(date) = DATE(:fecha)", nativeQuery = true)
	List<SellDollars> findByDateWithoutTime(@Param("fecha") LocalDate fecha);
	
	Supplier findById(int id);
	List<SellDollars> findByAsignadoFalse();
	
	List<SellDollars> findByDateBetween(LocalDateTime start, LocalDateTime end);
	List<SellDollars> findByClienteId(Integer clienteId);
	List<SellDollars> findByCliente_IdOrderByDateDesc(Integer clienteId);
	Optional<SellDollars> findByDedupeKey(String dedupeKey);
	List<SellDollars> findBySupplier_IdOrderByDateDesc(Integer supplierId);

}
