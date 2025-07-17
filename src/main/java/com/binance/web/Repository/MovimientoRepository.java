package com.binance.web.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.Movimiento;

public interface MovimientoRepository extends JpaRepository<Movimiento, Integer> {

}
