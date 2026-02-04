package com.binance.web.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.SaleP2pAccountCop;

@Repository
public interface SaleP2pAccountCopRepository extends JpaRepository<SaleP2pAccountCop, Integer> {

    List<SaleP2pAccountCop> findBySaleP2p_Id(Integer saleP2pId);

    long countBySaleP2p_Id(Integer saleP2pId);
}
