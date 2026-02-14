package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.binance.web.Entity.BalanceGeneral;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.BuyP2P;

public interface BuyP2PRepository extends JpaRepository<BuyP2P, Integer> {

    boolean existsByNumberOrder(String numberOrder);

    @Query("""
        SELECT b FROM BuyP2P b
        WHERE b.date >= :start AND b.date < :end
    """)
    List<BuyP2P> findByDateBetween(@Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);

    @Query("""
        SELECT b FROM BuyP2P b
        WHERE b.binanceAccount.id = :accountId
          AND b.date >= :start AND b.date < :end
          AND b.asignado = false
    """)
    List<BuyP2P> findNoAsignadasByAccountAndDateBetween(@Param("accountId") Integer accountId,
                                                       @Param("start") LocalDateTime start,
                                                       @Param("end") LocalDateTime end);

    @Query("""
        SELECT b FROM BuyP2P b
        WHERE b.date >= :start AND b.date < :end
          AND b.asignado = false
    """)
    List<BuyP2P> findNoAsignadasByDateBetween(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    List<BuyP2P> findByAsignadoFalseAndDateBetween(LocalDateTime start, LocalDateTime end);
    
    List<BuyP2P> findByAsignadoFalseAndDateLessThan(LocalDateTime end);


}
