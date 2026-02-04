package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.SaleP2pAccountCop;

@Repository
public interface SaleP2pAccountCopRepository extends JpaRepository<SaleP2pAccountCop, Integer> {

    List<SaleP2pAccountCop> findBySaleP2p_Id(Integer saleP2pId);

    long countBySaleP2p_Id(Integer saleP2pId);
    
    @Query("select coalesce(sum(d.amount),0) from SaleP2pAccountCop d where d.accountCop.id = :accId")
    double sumAllByAccount(@Param("accId") Integer accId);

    @Query("""
    select coalesce(sum(d.amount),0)
    from SaleP2pAccountCop d
    join d.saleP2p s
    where d.accountCop.id = :accId
    and s.date >= :start and s.date < :end
    """)
    double sumByAccountBetween(@Param("accId") Integer accId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

}
