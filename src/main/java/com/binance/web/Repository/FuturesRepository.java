package com.binance.web.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.Futures;

public interface FuturesRepository extends JpaRepository<Futures, Integer> {

}
