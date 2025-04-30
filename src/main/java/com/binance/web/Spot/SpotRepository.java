package com.binance.web.Spot;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.Spot;

public interface SpotRepository extends JpaRepository<Spot, Integer> {

}
