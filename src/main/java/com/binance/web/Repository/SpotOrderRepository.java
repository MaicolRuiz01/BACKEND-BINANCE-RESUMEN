package com.binance.web.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.SpotOrder;

public interface SpotOrderRepository extends JpaRepository<SpotOrder, Long> {
	  boolean existsByAccountAndOrderId(AccountBinance account, Long orderId);
	  List<SpotOrder> findByAccount_NameOrderByFilledAtDesc(String accountName);
	}

