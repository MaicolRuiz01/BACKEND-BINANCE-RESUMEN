package com.binance.web.Repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.binance.web.Entity.SellDollars;

public interface SellDollarsRepository extends JpaRepository<SellDollars, Integer> {
	@Query(value = "SELECT * FROM sell_dollars WHERE DATE(date) = :fecha", nativeQuery = true)
	List<SellDollars> findByDateWithoutTime(@Param("fecha") Date fecha);
}
