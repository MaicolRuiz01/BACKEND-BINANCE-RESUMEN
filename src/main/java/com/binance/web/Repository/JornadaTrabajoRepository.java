package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.JornadaTrabajo;

public interface JornadaTrabajoRepository extends JpaRepository<JornadaTrabajo, Integer> {

    /** Jornada EN CURSO de un usuario (la más reciente sin cerrar), si existe. */
    Optional<JornadaTrabajo> findFirstByUsernameAndEndedAtIsNullOrderByStartedAtDesc(String username);

    /** Jornadas cuyo inicio cae dentro del rango [inicio, fin). Para el resumen por día. */
    List<JornadaTrabajo> findByStartedAtBetween(LocalDateTime inicio, LocalDateTime fin);

    /** Jornadas en curso (globales), para saber quién está trabajando ahora. */
    List<JornadaTrabajo> findByEndedAtIsNull();
}
