package com.binance.web.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.SaleP2P;

@Repository
public interface SaleP2PRepository extends JpaRepository<SaleP2P, Integer> {
	
	boolean existsByNumberOrder(String numberOrder);

    @Query("""
        SELECT s FROM SaleP2P s
        WHERE s.date >= :start AND s.date < :end
    """)
    List<SaleP2P> findByDateBetween(@Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    @Query("""
        SELECT s FROM SaleP2P s
        WHERE s.date >= :start AND s.date < :end
          AND s.asignado = false
    """)
    List<SaleP2P> findNoAsignadasByDateBetween(@Param("start") LocalDateTime start,
                                              @Param("end") LocalDateTime end);

    // Si tu método actual findByDateAndBinanceAccount ya existe, déjalo.
    // Si NO existe, usa este:
    @Query("""
        SELECT s FROM SaleP2P s
        WHERE s.binanceAccount.id = :accountId
          AND s.date >= :start AND s.date < :end
    """)
    List<SaleP2P> findByAccountAndDateBetween(@Param("accountId") Integer accountId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    @Query("""
        SELECT s FROM SaleP2P s
        WHERE s.binanceAccount.id = :accountId
          AND s.date >= :start AND s.date < :end
          AND s.asignado = false
    """)
    List<SaleP2P> findNoAsignadasByAccountAndDateBetween(@Param("accountId") Integer accountId,
                                                        @Param("start") LocalDateTime start,
                                                        @Param("end") LocalDateTime end);

    default List<SaleP2P> findByDay(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return findByDateBetween(start, end);
      }
    List<SaleP2P> findByBinanceAccount_IdAndAsignadoFalse(Integer accountId);

}
