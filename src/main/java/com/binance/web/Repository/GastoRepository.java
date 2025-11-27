package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.Gasto;

public interface GastoRepository extends JpaRepository<Gasto, Integer> {
	
	List<Gasto> findByCuentaPago_IdAndFechaBetween(
            Integer cuentaId,
            LocalDateTime inicio,
            LocalDateTime fin
    );

    // Gastos pagados con una caja (Efectivo) en un rango de fechas
    List<Gasto> findByPagoEfectivo_IdAndFechaBetween(
            Integer cajaId,
            LocalDateTime inicio,
            LocalDateTime fin
    );

}
