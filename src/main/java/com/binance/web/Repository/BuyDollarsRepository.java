package com.binance.web.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.BuyDollars;

@Repository
public interface BuyDollarsRepository extends JpaRepository<BuyDollars, Integer>{
	@Query(value = "SELECT * FROM buy_dollars WHERE DATE(date) = DATE(:fecha)", nativeQuery = true)
	List<BuyDollars> findByDateWithoutTime(LocalDate fecha);
	BuyDollars findTopByOrderByDateDesc();
	@Query("SELECT b FROM BuyDollars b WHERE b.asignada = false AND b.date BETWEEN :start AND :end")
	List<BuyDollars> findNoAsignadasHoy(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
	List<BuyDollars> findByCliente_IdOrderByDateDesc(Integer clienteId);
	List<BuyDollars> findBySupplier_IdOrderByDateDesc(Integer supplierId);
	/** Compras del proveedor acotadas a un rango de fechas (para el resumen del día — rápido). */
	List<BuyDollars> findBySupplier_IdAndDateBetween(Integer supplierId, LocalDateTime desde, LocalDateTime hasta);
	Optional<BuyDollars> findByDedupeKey(String dedupeKey);
	List<BuyDollars> findByAsignadaFalseAndDateBetween(LocalDateTime start, LocalDateTime end);
	List<BuyDollars> findByAsignadaFalseOrderByDateDesc();
	List<BuyDollars> findByAsignadaFalse();
	List<BuyDollars> findByAsignadaFalseAndDateLessThan(LocalDateTime end);

	/** NULL-safe: incluye compras con asignada=null (el derived query las ignora). */
	@Query("SELECT c FROM BuyDollars c WHERE COALESCE(c.asignada, false) = false AND c.date < :end")
	List<BuyDollars> findNoAsignadasBeforeNullSafe(@Param("end") LocalDateTime end);
	/** Cuántas compras (dollars) siguen sin asignar — para el corte por sesión de la tasa promedio. */
	long countByAsignadaFalse();

	/**
	 * Suma en USDT de las DEMÁS compras que siguen sin asignar (excluye la que se está asignando
	 * ahora). Ese USDT ya está físicamente en el wallet (por eso getTotalExternalBalance() lo
	 * incluye) pero todavía no tiene tasa real asociada, así que debe restarse del saldoInicial
	 * de la sesión para no contarlo dos veces cuando, más tarde, le toque su propia asignación.
	 */
	@Query("SELECT COALESCE(SUM(b.amount), 0) FROM BuyDollars b "
			+ "WHERE COALESCE(b.asignada, false) = false "
			+ "AND b.id <> :excludeId "
			+ "AND (b.cryptoSymbol IS NULL OR UPPER(b.cryptoSymbol) = 'USDT')")
	Double sumAmountPendienteExcluyendo(@Param("excludeId") Integer excludeId);

	/** Solo IDs — evita cargar entidades completas para deduplicación */
	@Query("SELECT b.idDeposit FROM BuyDollars b WHERE b.idDeposit IS NOT NULL")
	java.util.Set<String> findAllDepositIds();

}
