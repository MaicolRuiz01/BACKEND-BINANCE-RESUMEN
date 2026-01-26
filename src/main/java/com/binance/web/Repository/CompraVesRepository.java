package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.CompraVES;

public interface CompraVesRepository extends JpaRepository<CompraVES, Long> {
	List<CompraVES> findByDateBetweenOrderByDateDesc(LocalDateTime start, LocalDateTime end);
	List<CompraVES> findByCuentaVesIdOrderByDateDesc(Integer cuentaVesId);
}
