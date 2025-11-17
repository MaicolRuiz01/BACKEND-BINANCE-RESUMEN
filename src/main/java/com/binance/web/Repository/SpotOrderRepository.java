package com.binance.web.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.SpotOrder;

@Repository
public interface SpotOrderRepository extends JpaRepository<SpotOrder, Long> {
	boolean existsByCuentaBinanceAndIdOrdenBinance(AccountBinance cuenta, Long idOrdenBinance);
	List<SpotOrder> findByCuentaBinance_NameOrderByFechaOperacionDesc(String cuenta);
	}

