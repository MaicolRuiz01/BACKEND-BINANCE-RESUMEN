package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.Deduccion;

public interface DeduccionRepository extends JpaRepository<Deduccion, Integer> {

    List<Deduccion> findAllByOrderByFechaDesc();

    List<Deduccion> findByFechaBetweenOrderByFechaDesc(LocalDateTime inicio, LocalDateTime fin);

    /** Para la idempotencia: si ya existe una con la misma clave, no se crea otra. */
    Optional<Deduccion> findByIdempotencyKey(String idempotencyKey);
}
