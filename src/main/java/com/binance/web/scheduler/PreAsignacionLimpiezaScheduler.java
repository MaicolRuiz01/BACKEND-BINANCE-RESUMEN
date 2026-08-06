package com.binance.web.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.BinanceAPI.P2PActiveOrderService;
import com.binance.web.Entity.P2PPreAsignacion;
import com.binance.web.Repository.P2PPreAsignacionRepository;
import com.binance.web.Repository.SaleP2PRepository;
import com.binance.web.dto.ActiveP2POrderDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Limpia las pre-asignaciones que quedaron huérfanas.
 *
 * Una pre-asignación se borra sola cuando la orden se completa (la consume P2PSyncService) o
 * cuando el operador la quita a mano. Pero si la orden se CANCELA o simplemente se cae, su fila
 * se queda ahí para siempre: la tabla crece y ensucia cualquier diagnóstico posterior.
 *
 * Criterio deliberadamente conservador — solo se borra si se cumplen las TRES condiciones:
 *   1. Tiene más de {@code p2p.preasignacion.expira-horas} horas de creada.
 *   2. Su orden YA NO está entre las activas de Binance.
 *   3. NO existe una venta registrada con ese número de orden.
 *
 * La tercera es la importante: si existe la venta, la pre-asignación ya cumplió y se borra por
 * la vía normal. Y el margen de horas debe ser MAYOR que la ventana de sync (lookback-horas),
 * porque si no se podría borrar la pre-asignación de una orden que todavía estaba por importarse
 * — y esa orden entraría sin asignar, con el dinero ya en la cuenta COP pero sin registro.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreAsignacionLimpiezaScheduler {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    private final P2PPreAsignacionRepository preAsignacionRepository;
    private final SaleP2PRepository saleP2PRepository;
    private final P2PActiveOrderService activeOrderService;

    /** Antigüedad mínima para considerar huérfana una pre-asignación. */
    @Value("${p2p.preasignacion.expira-horas:48}")
    private long expiraHoras;

    /** Corre una vez por hora: es una tarea de mantenimiento, no necesita más frecuencia. */
    @Scheduled(fixedDelayString = "${p2p.preasignacion.limpieza-interval-ms:3600000}",
               initialDelay = 300000)
    @Transactional
    public void limpiarHuerfanas() {
        try {
            LocalDateTime corte = LocalDateTime.now(ZONE).minusHours(expiraHoras);
            List<P2PPreAsignacion> viejas = preAsignacionRepository.findByCreatedAtBefore(corte);
            if (viejas.isEmpty()) return;

            // Órdenes que siguen vivas en Binance: esas no se tocan aunque lleven días.
            Set<String> activas = new HashSet<>();
            try {
                for (ActiveP2POrderDto o : activeOrderService.getAllActiveOrders()) {
                    activas.add(o.getOrderNumber());
                }
            } catch (Exception e) {
                // Si no se pudo confirmar cuáles siguen activas, NO se borra nada: es preferible
                // dejar filas de más a eliminar la pre-asignación de una orden viva.
                log.warn("[PreAsignLimpieza] No se pudo leer las órdenes activas, se omite el ciclo: {}",
                        e.getMessage());
                return;
            }

            int borradas = 0;
            for (P2PPreAsignacion pre : viejas) {
                String orden = pre.getOrderNumber();
                if (orden == null || orden.isBlank()) continue;
                if (activas.contains(orden)) continue;                       // sigue en curso
                if (saleP2PRepository.existsByNumberOrder(orden)) continue;   // ya se importó

                preAsignacionRepository.delete(pre);
                borradas++;
                log.info("[PreAsignLimpieza] Pre-asignación huérfana eliminada: orden {} (cuenta {})",
                        orden, pre.getCuentaCop() != null ? pre.getCuentaCop().getName() : "?");
            }

            if (borradas > 0) {
                log.info("[PreAsignLimpieza] {} pre-asignación(es) huérfana(s) eliminada(s).", borradas);
            }
        } catch (Exception e) {
            log.warn("[PreAsignLimpieza] Error limpiando pre-asignaciones: {}", e.getMessage());
        }
    }
}
