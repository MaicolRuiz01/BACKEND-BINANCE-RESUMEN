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
import com.binance.web.model.CompraP2PCuentaDTO;

public interface BuyP2PRepository extends JpaRepository<BuyP2P, Integer> {

    boolean existsByNumberOrder(String numberOrder);

    /** Compras P2P asignadas a una cuenta COP (proyección liviana, 1 query). */
    @Query("""
        SELECT new com.binance.web.model.CompraP2PCuentaDTO(
            b.id, b.numberOrder, b.date, b.tasa, b.dollarsUs, b.pesosCop, d.amount, ba.name)
        FROM BuyP2pAccountCop d JOIN d.buyP2p b
        LEFT JOIN b.binanceAccount ba
        WHERE d.accountCop.id = :cuentaId
        ORDER BY b.date DESC
    """)
    List<CompraP2PCuentaDTO> findComprasP2PByAccountCop(@Param("cuentaId") Integer cuentaId);

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
