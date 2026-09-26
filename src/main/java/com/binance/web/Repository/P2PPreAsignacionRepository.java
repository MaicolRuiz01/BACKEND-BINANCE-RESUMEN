package com.binance.web.Repository;

import com.binance.web.Entity.P2PPreAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface P2PPreAsignacionRepository extends JpaRepository<P2PPreAsignacion, Long> {

    Optional<P2PPreAsignacion> findByOrderNumber(String orderNumber);

    void deleteByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    /** De estas órdenes, cuáles tienen pre-asignación. Una sola consulta por lote. */
    @Query("SELECT p.orderNumber FROM P2PPreAsignacion p WHERE p.orderNumber IN :numeros")
    java.util.Set<String> findOrderNumbersIn(
            @org.springframework.data.repository.query.Param("numeros") java.util.Collection<String> numeros);

    /**
     * Pre-asignaciones creadas antes de una fecha. Se usa para limpiar las que quedaron
     * huérfanas: órdenes que se cancelaron o se cayeron y por lo tanto nunca se completaron,
     * así que su fila no se borra nunca por la vía normal.
     */
    List<P2PPreAsignacion> findByCreatedAtBefore(LocalDateTime fecha);

    /** Filas guardadas antes de que existiera la columna pesos_cop (se completan en el poll). */
    List<P2PPreAsignacion> findByPesosCopIsNull();

    /**
     * Pre-asignaciones cuya venta todavía no se importó (candidatas a "en curso").
     *
     * Se excluyen las filas cuya venta ya está registrada: esas ya no están "en curso" (su plata
     * ya pasó, o debe pasar, por la venta importada), así que sumarlas contaría dos veces.
     * OJO: no todas las que devuelve siguen en curso — puede haber huérfanas (órdenes canceladas o
     * vencidas). El filtro final contra las órdenes activas lo hace SaldosEnCursoService.
     */
    @Query("""
        SELECT p.cuentaCop.id AS copId, p.cuentaCop.name AS copNombre, p.orderNumber AS orderNumber,
               p.pesosCop AS pesosCop, p.createdAt AS createdAt
        FROM P2PPreAsignacion p
        WHERE NOT EXISTS (SELECT 1 FROM SaleP2P s WHERE s.numberOrder = p.orderNumber)
    """)
    List<PreSinImportar> findSinImportar();

    interface PreSinImportar {
        Integer getCopId();
        String getCopNombre();
        String getOrderNumber();
        Double getPesosCop();
        LocalDateTime getCreatedAt();
    }
}
