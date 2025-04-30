package com.binance.web.BuyDollars;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.BuyDollars;

@Repository
public interface BuyDollarsRepository extends JpaRepository<BuyDollars, Integer>{

}
