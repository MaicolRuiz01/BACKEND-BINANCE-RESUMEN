package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.SesionOperador;

public interface SesionOperadorRepository extends JpaRepository<SesionOperador, Integer> {

    /** Sesiones cuyo inicio (loginAt) cae dentro del rango [inicio, fin). Para el resumen por día. */
    List<SesionOperador> findByLoginAtBetween(LocalDateTime inicio, LocalDateTime fin);
}
