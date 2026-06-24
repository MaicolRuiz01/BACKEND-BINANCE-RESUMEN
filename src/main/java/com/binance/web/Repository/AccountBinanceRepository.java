package com.binance.web.Repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.AccountBinance;

@Repository
public interface AccountBinanceRepository  extends JpaRepository<AccountBinance, Integer>{
	AccountBinance findByName(String name);

	AccountBinance findByUserBinance(String name);

	AccountBinance findByReferenceAccount(String referenceAccount);

	AccountBinance findById(int id);

	List<AccountBinance> findByTipo(String tipo);

	/** Solo cuentas activas de un tipo — para no consultar APIs de cuentas desactivadas */
	List<AccountBinance> findByTipoAndActivaTrue(String tipo);

	AccountBinance findFirstByNameIgnoreCase(String name);

	/** Verifica si ya existe una cuenta con esa dirección (para crear) */
	boolean existsByAddress(String address);

	/** Verifica si ya existe otra cuenta con esa dirección (para editar, excluyendo el propio id) */
	boolean existsByAddressAndIdNot(String address, Integer id);

	/** Solo direcciones — evita cargar entidades completas */
	@Query("SELECT a.address FROM AccountBinance a WHERE a.address IS NOT NULL AND a.address <> ''")
	Set<String> findAllAddresses();

	/** Solo userBinance — evita cargar entidades completas */
	@Query("SELECT a.userBinance FROM AccountBinance a WHERE a.userBinance IS NOT NULL AND a.userBinance <> ''")
	Set<String> findAllUserBinances();

	/** Direcciones de wallets TRUST solamente */
	@Query("SELECT a.address FROM AccountBinance a WHERE UPPER(a.tipo) = 'TRUST' AND a.address IS NOT NULL AND a.address <> ''")
	List<String> findTrustAddresses();

	/** Cuentas BINANCE con API keys configuradas */
	@Query("SELECT a.name FROM AccountBinance a WHERE UPPER(a.tipo) = 'BINANCE' AND a.apiKey IS NOT NULL AND a.apiSecret IS NOT NULL")
	List<String> findBinanceAccountNames();

}
