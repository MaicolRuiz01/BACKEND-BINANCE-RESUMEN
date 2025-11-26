package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.SellDollarsAccountCop;

@Repository
public interface SellDollarsAccountCopRepository
        extends JpaRepository<SellDollarsAccountCop, Integer> {

    // todas las asignaciones de una cuenta COP para ventas de hoy (rango fecha)
    List<SellDollarsAccountCop> findByAccountCop_IdAndSellDollars_DateBetween(
            Integer accountCopId,
            LocalDateTime desde,
            LocalDateTime hasta
    );
}
