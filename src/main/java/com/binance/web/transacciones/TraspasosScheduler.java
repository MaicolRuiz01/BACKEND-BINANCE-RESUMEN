package com.binance.web.transacciones;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Registra automáticamente los traspasos internos del día en segundo plano.
 *
 * NO reemplaza al on-demand: la vista Movimientos → Traspasos sigue llamando a
 * /transacciones/hoy cuando el usuario abre o actualiza. Este scheduler es un
 * respaldo para que se capturen aunque nadie abra la pestaña.
 *
 * Intervalo configurable: traspasos.sync.interval-ms (3 min por defecto).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraspasosScheduler {

    private final TransaccionesService transaccionesService;
    private final TraspasoReconciliacionService reconciliacionService;

    @Scheduled(fixedDelayString = "${traspasos.sync.interval-ms:180000}")
    public void registrarTraspasosAutomaticamente() {
        try {
            var lista = transaccionesService.saveAndFetchTodayTraspasos();
            if (lista != null && !lista.isEmpty()) {
                log.debug("[TraspasosScheduler] Traspasos del día procesados: {}", lista.size());
            }
        } catch (Exception e) {
            log.warn("[TraspasosScheduler] Error registrando traspasos: {}", e.getMessage());
        }
        try {
            // Emparejar compras/ventas sin asignar que en realidad son traspasos internos (Binance↔TRON).
            reconciliacionService.reconciliarTraspasos();
        } catch (Exception e) {
            log.warn("[TraspasosScheduler] Error reconciliando traspasos: {}", e.getMessage());
        }
    }
}
