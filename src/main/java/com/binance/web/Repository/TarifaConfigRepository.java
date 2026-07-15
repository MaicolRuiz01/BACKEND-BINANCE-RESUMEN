package com.binance.web.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.TarifaConfig;

public interface TarifaConfigRepository extends JpaRepository<TarifaConfig, Integer> {
}
