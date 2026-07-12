package com.binance.web.transacciones;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

	/**
	 * Listado liviano y paginado de traspasos para la vista (de, a, fecha, cantidad, moneda).
	 * Proyección por interfaz: NO carga las entidades AccountBinance completas, solo el nombre,
	 * así la tabla carga rápido aunque haya muchos traspasos.
	 */
	@Query(value = """
			SELECT t.fecha AS fecha, t.cantidad AS cantidad, t.tipo AS moneda,
			       cf.name AS deQuien, ct.name AS aQuien, t.idtransaccion AS idtransaccion
			FROM Transacciones t
			LEFT JOIN t.cuentaFrom cf
			LEFT JOIN t.cuentaTo ct
			ORDER BY t.fecha DESC
			""",
			countQuery = "SELECT COUNT(t) FROM Transacciones t")
	Page<TraspasoListItem> findTraspasosPaginados(Pageable pageable);

	/** Proyección liviana para el listado de traspasos. */
	interface TraspasoListItem {
		LocalDateTime getFecha();
		Double getCantidad();
		String getMoneda();
		String getDeQuien();
		String getAQuien();
		String getIdtransaccion();
	}

}
