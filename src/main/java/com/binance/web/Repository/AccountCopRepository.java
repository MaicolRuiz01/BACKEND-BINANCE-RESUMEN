package com.binance.web.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

}
