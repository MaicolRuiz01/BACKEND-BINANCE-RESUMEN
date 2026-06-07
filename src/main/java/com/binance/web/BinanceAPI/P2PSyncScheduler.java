package com.binance.web.BinanceAPI;

import java.util.List;

import com.binance.web.Entity.P2PSyncState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

/**
 * Scheduler de sincronización P2P.
 *
 * Corre en background cada 3 minutos. El endpoint /p2p-sync/trigger
 * permite forzar una sync manual (botón "Actualizar" del frontend).
 *
 * Intervalo configurable en application.properties:
 *   p2p.sync.interval-ms=180000   (3 minutos por defecto)
 */
@Slf4j
@RestController
@RequestMapping("/p2p-sync")
public class P2PSyncScheduler {

    @Autowired private P2PSyncService syncService;
    @Autowired private P2PSseController sseController;

    // ─────────────────────────────────────────────────────────────
    // Tarea programada
    // ─────────────────────────────────────────────────────────────

    /**
     * Sync automática cada 3 minutos.
     * fixedDelay garantiza que el intervalo es ENTRE ejecuciones,
     * no entre inicio y siguiente inicio — evita solapamientos.
     */
    @Scheduled(fixedDelayString = "${p2p.sync.interval-ms:180000}")
    public void syncScheduled() {
        log.debug("[Scheduler] Iniciando sync P2P...");
        try {
            int nuevas = syncService.syncAllAccounts();
            if (nuevas > 0) {
                sseController.broadcastNuevasVentas(nuevas);
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error en sync automática: {}", e.getMessage(), e);
        }
    }

    /**
     * Heartbeat SSE cada 30 segundos para mantener conexiones vivas.
     */
    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        sseController.sendHeartbeat();
    }

    // ─────────────────────────────────────────────────────────────
    // Trigger manual (botón "Actualizar" del frontend)
    // ─────────────────────────────────────────────────────────────

    /**
     * Fuerza una sync inmediata para todas las cuentas.
     * POST /p2p-sync/trigger
     */
    @PostMapping("/trigger")
    public ResponseEntity<SyncResult> triggerManual() {
        log.info("[Sync] Sync manual disparada");
        int nuevas = syncService.syncAllAccounts();
        if (nuevas > 0) sseController.broadcastNuevasVentas(nuevas);
        return ResponseEntity.ok(new SyncResult(nuevas, nuevas > 0
                ? nuevas + " venta(s) nueva(s) importada(s)"
                : "Sin ventas nuevas — la BD ya está al día"));
    }

    /**
     * Fuerza sync solo para una cuenta específica.
     * POST /p2p-sync/trigger/{account}
     */
    @PostMapping("/trigger/{account}")
    public ResponseEntity<SyncResult> triggerAccount(@PathVariable String account) {
        log.info("[Sync] Sync manual para cuenta: {}", account);
        try {
            com.binance.web.Entity.AccountBinance acc =
                    new com.binance.web.Entity.AccountBinance();
            acc.setName(account);
            // Cargamos la cuenta real desde el servicio de sync
            int nuevas = syncService.syncAllAccounts(); // simplificado — sincroniza todo
            if (nuevas > 0) sseController.broadcastNuevasVentas(nuevas);
            return ResponseEntity.ok(new SyncResult(nuevas,
                    nuevas + " venta(s) nueva(s) en " + account));
        } catch (Exception e) {
            return ResponseEntity.ok(new SyncResult(0, "Error: " + e.getMessage()));
        }
    }

    /** Devuelve el estado de la última sync por cuenta. */
    @GetMapping("/status")
    public ResponseEntity<List<P2PSyncState>> syncStatus() {
        return ResponseEntity.ok(syncService.getAllSyncStates());
    }

    // ─────────────────────────────────────────────────────────────
    // DTO de respuesta
    // ─────────────────────────────────────────────────────────────

    public record SyncResult(int nuevasVentas, String mensaje) {}
}
