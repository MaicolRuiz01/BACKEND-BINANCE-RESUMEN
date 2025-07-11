package com.binance.web.transacciones;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.Transacciones;

public interface TransaccionesRepository extends JpaRepository<Transacciones, Integer> {
	
	List<Transacciones> findByFechaBetween(LocalDateTime inicio, LocalDateTime fin);

}
