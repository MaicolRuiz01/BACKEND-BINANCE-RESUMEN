package com.binance.web.Repository;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.binance.web.Entity.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

	/** Solo wallets no nulas — para lookup rápido sin cargar toda la entidad */
	@Query("SELECT c FROM Cliente c WHERE c.wallet IS NOT NULL AND c.wallet <> ''")
	java.util.List<Cliente> findByWalletNotNull();

	/** Solo clientes con binanceId configurado */
	@Query("SELECT c FROM Cliente c WHERE c.binanceId IS NOT NULL")
	java.util.List<Cliente> findByBinanceIdNotNull();

}
