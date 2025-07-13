package com.binance.web.Repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.balanceGeneral.BalanceGeneral;

public interface BalanceGeneralRepository extends JpaRepository<BalanceGeneral, Integer> {
    Optional<BalanceGeneral> findByDate(LocalDate date);

}
