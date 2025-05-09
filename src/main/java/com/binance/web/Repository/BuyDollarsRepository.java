package com.binance.web.Repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.BuyDollars;

@Repository
public interface BuyDollarsRepository extends JpaRepository<BuyDollars, Integer>{
	@Query(value = "SELECT * FROM buy_dollars WHERE DATE(date) = :fecha", nativeQuery = true)
	List<BuyDollars> findByDateWithoutTime(Date fecha);
}
