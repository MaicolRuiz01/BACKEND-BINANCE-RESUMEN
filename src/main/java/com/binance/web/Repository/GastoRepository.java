package com.binance.web.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.binance.web.Entity.Gasto;

public interface GastoRepository extends JpaRepository<Gasto, Integer> {

	/** Para el candado anti-duplicado: si ya existe un gasto con esta clave, no se crea otro. */
	Optional<Gasto> findByIdempotencyKey(String idempotencyKey);

	/** Listado liviano: solo ids de las relaciones (no carga la cuenta COP completa ni sus brebeKeys). */
	@Query("SELECT g.id AS id, g.descripcion AS descripcion, g.fecha AS fecha, g.monto AS monto, "
			+ "cp.id AS cuentaPagoId, pe.id AS pagoEfectivoId "
			+ "FROM Gasto g LEFT JOIN g.cuentaPago cp LEFT JOIN g.pagoEfectivo pe "
			+ "ORDER BY g.fecha DESC")
	List<GastoListView> findAllLite();

	interface GastoListView {
		Integer getId();
		String getDescripcion();
		LocalDateTime getFecha();
		Double getMonto();
		Integer getCuentaPagoId();
		Integer getPagoEfectivoId();
	}

	List<Gasto> findByCuentaPago_IdAndFechaBetween(
            Integer cuentaId,
            LocalDateTime inicio,
            LocalDateTime fin
    );

    // Gastos pagados con una caja (Efectivo) en un rango de fechas
    List<Gasto> findByPagoEfectivo_IdAndFechaBetween(
            Integer cajaId,
            LocalDateTime inicio,
            LocalDateTime fin
    );

}
