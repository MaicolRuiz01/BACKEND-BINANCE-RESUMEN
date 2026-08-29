package com.binance.web.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.binance.web.service.BuyDollarsService;

import lombok.extern.slf4j.Slf4j;

/**
 * Trae las compras solo, cada 3 minutos.
 *
 * POR QUÉ EXISTE: no había ningún proceso automático que importara compras. Las ventas P2P
 * tienen su scheduler, los traspasos también, el 4x1000 también — las compras no. Lo único que
 * disparaba la importación era abrir la pantalla de Asignaciones → Compras.
 *
 * Combinado con que cada fuente miraba solo el día de hoy, el resultado era que una compra hecha
 * de noche, si nadie abría esa pantalla antes de medianoche, quedaba fuera para siempre: no le
 * acreditaba al proveedor, no entraba a la tasa promedio y no sumaba al balance. Por eso el
 * cliente reportaba que "a veces el sistema no agarra todas las compras": no era aleatorio,
 * dependía de si alguien había abierto la pantalla a tiempo.
 *
 * Reimportar es seguro: cada compra se filtra por idDeposit y por dedupeKey, así que pasar
 * muchas veces sobre la misma ventana no duplica nada.
 *
 * Intervalo configurable:  compras.sync.interval-ms=180000   (3 minutos por defecto)
 * Ventana hacia atrás:     compras.sync.lookback-horas=36    (en BuyDollarsServiceImpl)
 */
@Slf4j
@Component
public class ComprasSyncScheduler {

    @Autowired
    private BuyDollarsService buyDollarsService;

    /**
     * fixedDelay (y no fixedRate) para que el intervalo cuente ENTRE ejecuciones: si una corrida
     * se demora porque las APIs están lentas, la siguiente no se le encima.
     *
     * initialDelay da tiempo a que la aplicación termine de levantar antes de salir a la red.
     */
    @Scheduled(fixedDelayString = "${compras.sync.interval-ms:180000}", initialDelay = 45_000)
    public void importarCompras() {
        try {
            buyDollarsService.registrarComprasAutomaticamente();
        } catch (Exception e) {
            // Nunca se propaga: si esto lanzara, Spring cancelaría la tarea programada y no se
            // volvería a intentar hasta reiniciar la aplicación. El detalle de qué se importó y
            // qué se descartó queda en el log de BuyDollarsServiceImpl.
            log.error("[ComprasSync] Falló la importación automática de compras: {}", e.getMessage(), e);
        }
    }
}
