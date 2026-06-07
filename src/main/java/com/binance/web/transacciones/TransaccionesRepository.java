package com.binance.web.transacciones;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.binance.web.Entity.Transacciones;

public interface TransaccionesRepository extends JpaRepository<Transacciones, Integer> {

	List<Transacciones> findByFechaBetween(LocalDateTime inicio, LocalDateTime fin);
	boolean existsByTxId(String txId);
	Optional<Transacciones> findByTxId(String txId);

	/** Solo IDs — evita cargar entidades completas para deduplicación */
	@Query("SELECT t.idtransaccion FROM Transacciones t WHERE t.idtransaccion IS NOT NULL")
	Set<String> findAllTransaccionIds();

}
