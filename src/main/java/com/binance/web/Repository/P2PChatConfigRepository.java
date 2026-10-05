package com.binance.web.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.P2PChatConfig;

public interface P2PChatConfigRepository extends JpaRepository<P2PChatConfig, Integer> {
}
