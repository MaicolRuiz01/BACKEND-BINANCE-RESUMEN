package com.binance.web.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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
	 *
	 * OJO (incidente 14/08/2026): esto NO fue suficiente en la práctica. El
	 * lock de fila evita que dos escrituras se pisen, pero si el mismo
	 * Efectivo ya estaba cargado en el contexto de persistencia de ESTA
	 * transacción antes de este SELECT (ej. vía retirador.getEfectivo()),
	 * Hibernate puede devolver la instancia ya cacheada en vez de refrescar
	 * su campo saldo con la fila recién bloqueada — el lock se adquiere bien,
	 * pero el valor en memoria usado para sumar sigue viejo. Por eso, para
	 * sumar/restar saldo, usar SIEMPRE incrementarSaldo() de abajo (UPDATE
	 * atómico en una sola sentencia SQL) en vez de leer-sumar-guardar.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Efectivo e where e.id = :id")
	Optional<Efectivo> findByIdForUpdate(@Param("id") Integer id);

	/**
	 * Suma (delta positivo) o resta (delta negativo) el saldo de la caja de
	 * forma ATÓMICA — el "saldo = saldo + :delta" lo hace MySQL en una sola
	 * sentencia, no depende de que Java lea un valor y lo vuelva a escribir,
	 * así que es inmune a que dos confirmaciones casi simultáneas se pisen
	 * entre sí (el bug real detrás del incidente del 14/08/2026 con la caja
	 * de Sebastian: 6 retiros confirmados en 44 segundos, y los 6 sumaron
	 * sobre el MISMO saldo viejo en vez de acumularse).
	 */
	@Modifying
	@Transactional
	@Query("update Efectivo e set e.saldo = e.saldo + :delta where e.id = :id")
	int incrementarSaldo(@Param("id") Integer id, @Param("delta") Double delta);

}
