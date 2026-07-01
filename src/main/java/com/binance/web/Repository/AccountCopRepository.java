package com.binance.web.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.binance.web.Entity.SaleP2P;

import jakarta.persistence.LockModeType;

import com.binance.web.Entity.AccountCop;

public interface AccountCopRepository extends JpaRepository<AccountCop, Integer>{
	
	@Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountCop a where a.id = :id")
    Optional<AccountCop> findByIdForUpdate(@Param("id") Integer id);
	AccountCop findByName(String name);

	/** Verifica si ya existe una cuenta COP con ese número de cuenta (para crear) */
	boolean existsByNumeroCuenta(String numeroCuenta);

	/** Verifica si ya existe otra cuenta COP con ese número de cuenta (para editar) */
	boolean existsByNumeroCuentaAndIdNot(String numeroCuenta, Integer id);

	/** Resta atómica del saldo (no pierde la resta aunque otro proceso actualice el saldo a la vez). */
	@Modifying
	@Query("UPDATE AccountCop a SET a.balance = a.balance - :monto WHERE a.id = :id")
	int restarSaldo(@Param("id") Integer id, @Param("monto") double monto);

	/** Trae todas las cuentas con sus brebeKeys en UNA sola consulta (evita el N+1 del EAGER). */
	@Query("SELECT DISTINCT a FROM AccountCop a LEFT JOIN FETCH a.brebeKeys")
	List<AccountCop> findAllWithBrebeKeys();

	/** Solo id + saldo — consulta liviana para refrescar el saldo rápido. */
	@Query("SELECT a.id AS id, a.balance AS balance FROM AccountCop a")
	List<SaldoView> findAllSaldos();

	/** Proyección liviana: solo lo necesario para el saldo. */
	interface SaldoView {
		Integer getId();
		Double getBalance();
	}

}
