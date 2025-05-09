package com.binance.web.Repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.SaleP2P;

@Repository
public interface SaleP2PRepository extends JpaRepository<SaleP2P, Integer> {

    // Encontrar ventas asociadas a una cuenta de Binance
    List<SaleP2P> findByBinanceAccount(AccountBinance accountBinance);

    // Encontrar ventas por fecha sin hora
    @Query(value = "SELECT * FROM sale_p2p WHERE DATE(date) = :fecha", nativeQuery = true)
    List<SaleP2P> findByDateWithoutTime(@Param("fecha") Date fecha);

    // Método para encontrar las ventas que no han sido asignadas (campo 'assigned' = false)
    List<SaleP2P> findByAssignedFalse();

    // Método para marcar una venta como asignada
    @Modifying
    @Query("UPDATE SaleP2P s SET s.assigned = true WHERE s.id = :id")
    void markAsAssigned(@Param("id") Integer id);

}
