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
import com.binance.web.Entity.BankType;

public interface AccountCopRepository extends JpaRepository<AccountCop, Integer>{
	
	@Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountCop a where a.id = :id")
    Optional<AccountCop> findByIdForUpdate(@Param("id") Integer id);
	AccountCop findByName(String name);

	/** ¿Existe una cuenta con ese número EN EL MISMO BANCO? (para crear) */
	boolean existsByNumeroCuentaAndBankType(String numeroCuenta, BankType bankType);

	/** ¿Existe OTRA cuenta con ese número EN EL MISMO BANCO? (para editar) */
	boolean existsByNumeroCuentaAndBankTypeAndIdNot(String numeroCuenta, BankType bankType, Integer id);

	/** Resta atómica del saldo (no pierde la resta aunque otro proceso actualice el saldo a la vez). */
	@Modifying
	@Query("UPDATE AccountCop a SET a.balance = a.balance - :monto WHERE a.id = :id")
	int restarSaldo(@Param("id") Integer id, @Param("monto") double monto);

	/** Suma atómica del saldo (para devolver el dinero al eliminar un gasto). */
	@Modifying
	@Query("UPDATE AccountCop a SET a.balance = a.balance + :monto WHERE a.id = :id")
	int sumarSaldo(@Param("id") Integer id, @Param("monto") double monto);

	/** Trae todas las cuentas con sus brebeKeys en UNA sola consulta (evita el N+1 del EAGER). */
	@Query("SELECT DISTINCT a FROM AccountCop a LEFT JOIN FETCH a.brebeKeys")
	List<AccountCop> findAllWithBrebeKeys();

	/** Id + saldo + cupo diario restante — consulta liviana para refrescar en tiempo real (SSE). */
	@Query("""
		SELECT a.id AS id, a.balance AS balance,
		       a.cupoCajeroDisponibleHoy AS cupoCajeroDisponibleHoy,
		       a.cupoCorresponsalDisponibleHoy AS cupoCorresponsalDisponibleHoy
		FROM AccountCop a
	""")
	List<SaldoView> findAllSaldos();

	/** Proyección liviana: solo lo necesario para el saldo y el cupo del día. */
	interface SaldoView {
		Integer getId();
		Double getBalance();
		Double getCupoCajeroDisponibleHoy();
		Double getCupoCorresponsalDisponibleHoy();
	}

}
