package com.binance.web.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.BuyDollars;

@Repository
public interface BuyDollarsRepository extends JpaRepository<BuyDollars, Integer>{
	@Query(value = "SELECT * FROM buy_dollars WHERE DATE(date) = DATE(:fecha)", nativeQuery = true)
	List<BuyDollars> findByDateWithoutTime(LocalDate fecha);
	BuyDollars findTopByOrderByDateDesc();
	@Query("SELECT b FROM BuyDollars b WHERE b.asignada = false AND b.date BETWEEN :start AND :end")
	List<BuyDollars> findNoAsignadasHoy(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
	List<BuyDollars> findByCliente_IdOrderByDateDesc(Integer clienteId);
	List<BuyDollars> findBySupplier_IdOrderByDateDesc(Integer supplierId);
	Optional<BuyDollars> findByDedupeKey(String dedupeKey);
	List<BuyDollars> findByAsignadaFalseAndDateBetween(LocalDateTime start, LocalDateTime end);
	List<BuyDollars> findByAsignadaFalseOrderByDateDesc();
	List<BuyDollars> findByAsignadaFalse();
	List<BuyDollars> findByAsignadaFalseAndDateLessThan(LocalDateTime end);

}
