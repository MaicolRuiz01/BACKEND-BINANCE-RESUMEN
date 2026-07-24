package com.binance.web.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.PagoOperador;

public interface PagoOperadorRepository extends JpaRepository<PagoOperador, Integer> {

    /** Historial de pagos de un operador, del más reciente al más antiguo. */
    List<PagoOperador> findByUsernameOrderByFechaDesc(String username);

    /** ¿Ya se le pagó a este operador el día indicado? (para bloquear el doble pago). */
    boolean existsByUsernameAndDia(String username, LocalDate dia);
}
