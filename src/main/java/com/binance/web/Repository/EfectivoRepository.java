package com.binance.web.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.binance.web.Entity.Efectivo;

import jakarta.persistence.LockModeType;

public interface EfectivoRepository extends JpaRepository<Efectivo, Integer>{

	Efectivo findByName(String name);

	/**
	 * Trae la caja con un bloqueo exclusivo de fila (igual que
	 * AccountCopRepository.findByIdForUpdate). Si dos confirmaciones de retiro
	 * llegan casi al mismo tiempo para la MISMA caja, la segunda espera a que la
	 * primera termine su transacción en vez de leer un saldo desactualizado y
	 * pisar la suma de la primera (así se perdían sumas de caja cuando se
	 * confirmaban varios retiros muy seguido — ej. con el botón "Todo").
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Efectivo e where e.id = :id")
	Optional<Efectivo> findByIdForUpdate(@Param("id") Integer id);

}
