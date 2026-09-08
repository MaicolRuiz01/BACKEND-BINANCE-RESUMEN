package com.binance.web.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.binance.web.service.SellDollarsService;

import lombok.extern.slf4j.Slf4j;

/**
 * Trae las ventas (sell dollars) solo, cada 20 minutos.
 *
 * POR QUÉ EXISTE: la importación de ventas dependía por completo de que alguien abriera la
 * pantalla de Asignaciones → Ventas (ahí es donde el frontend dispara
 * POST /sell-dollars/importar-automatico). Si nadie la abría un día — un fin de semana, por
 * ejemplo — las ventas de ese día (incluidas las de Bybit) no quedaban registradas, y como cada
 * fuente filtra por "hoy", al otro día ya no había forma automática de recuperarlas. Así se
 * perdieron dos ventas de Bybit del sábado que el cliente reportó que no aparecían en la app.
 *
 * Con este scheduler corriendo 24/7, "hoy" siempre se cumple en algún momento del día aunque
 * nadie mire el frontend, así que las ventas quedan registradas (sin asignar) sin depender de
 * que alguien entre a la pantalla. El botón "Sincronizar ahora" del frontend sigue disponible
 * para forzar una corrida inmediata cuando el cliente quiere ver una venta reflejada al instante.
 *
 * Reimportar es seguro: cada venta se filtra por idWithdrawals/txId y por dedupeKey, así que
 * pasar muchas veces sobre la misma ventana no duplica nada.
 *
 * Intervalo configurable:  ventas.sync.interval-ms=1200000       (20 minutos por defecto)
 * Ventana hacia atrás:     ventas.sync.lookback-horas=36         (solo Bybit, en SellDollarsServiceImpl)
 */
@Slf4j
@Component
public class VentasSyncScheduler {

    @Autowired
    private SellDollarsService sellDollarsService;

    /**
     * fixedDelay (y no fixedRate) para que el intervalo cuente ENTRE ejecuciones: si una corrida
     * se demora porque las APIs están lentas, la siguiente no se le encima.
     *
     * initialDelay da tiempo a que la aplicación termine de levantar antes de salir a la red.
     */
    @Scheduled(fixedDelayString = "${ventas.sync.interval-ms:1200000}", initialDelay = 60_000)
    public void importarVentas() {
        try {
            sellDollarsService.registrarVentasAutomaticamente();
        } catch (Exception e) {
            // Nunca se propaga: si esto lanzara, Spring cancelaría la tarea programada y no se
            // volvería a intentar hasta reiniciar la aplicación. El detalle de qué se importó y
            // qué se descartó queda en el log de SellDollarsServiceImpl.
            log.error("[VentasSync] Falló la importación automática de ventas: {}", e.getMessage(), e);
        }
    }
}
