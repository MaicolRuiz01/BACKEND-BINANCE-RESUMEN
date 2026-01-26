package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.VentaVES;

public interface VentaVesRepository extends JpaRepository<VentaVES, Long> {
	List<VentaVES> findByDateBetweenOrderByDateDesc(LocalDateTime start, LocalDateTime end);
	List<VentaVES> findByCuentaVesIdOrderByDateDesc(Integer cuentaVesId);
}
