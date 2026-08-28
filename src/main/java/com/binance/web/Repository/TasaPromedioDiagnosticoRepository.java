package com.binance.web.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.TasaPromedioDiagnostico;

public interface TasaPromedioDiagnosticoRepository extends JpaRepository<TasaPromedioDiagnostico, Integer> {

    /** Los últimos movimientos, del más reciente al más viejo — para revisar qué pasó. */
    List<TasaPromedioDiagnostico> findTop200ByOrderByFechaDesc();
}
