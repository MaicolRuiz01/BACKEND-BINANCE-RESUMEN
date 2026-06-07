package com.binance.web.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.P2PSyncState;

@Repository
public interface P2PSyncStateRepository extends JpaRepository<P2PSyncState, Integer> {

    Optional<P2PSyncState> findByBinanceAccount_Name(String name);
}
