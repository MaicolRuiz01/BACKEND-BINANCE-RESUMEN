package com.binance.web.transacciones;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.Transacciones;

public interface TransaccionesRepository extends JpaRepository<Transacciones, Integer> {

}
