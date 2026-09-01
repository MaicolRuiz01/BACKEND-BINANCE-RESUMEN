package com.binance.web.activacion;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.conciliacion.ConciliacionBancariaService;
import com.binance.web.detencion.DetencionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CuentaP2PSyncServiceImpl implements CuentaP2PSyncService {

    // OJO: para "activar" NO se usa ActivacionService/activacion_solicitud —
    // esa cola quedó huérfana (pochonance_activador.py se reescribió hace un
    // tiempo para reutilizar la cola de conciliación en vez de tener una
    // segunda cola, y nada consume /movimientos/activacion/pendiente hoy).
    // El bot SÍ escucha GET /conciliacion/pendiente, así que usamos ese mismo
    // mecanismo, ya probado y funcionando.
    private final ConciliacionBancariaService conciliacionBancariaService;
    private final DetencionService detencionService;

    // INTERRUPTOR DE SEGURIDAD (agosto 2026, mismo criterio que USAR_PRODUCCION
    // en el bot y los bloques "fase de pruebas" que ya existían en este mismo
    // archivo de código antes de esto): en false por defecto, para poder
    // desplegar este código a producción sin que dispare nada automático
    // mientras el sistema de Movimientos sigue en uso con el flujo manual de
    // siempre. Se activa cambiando la variable de entorno en Railway
    // (app.cuentasp2p.auto-sync-habilitado=true) — NO requiere un redeploy.
    @Value("${app.cuentasp2p.auto-sync-habilitado:false}")
    private boolean autoSyncHabilitado;

    @Override
    public void sincronizar(AccountCop cuenta, boolean estabaActivaAntes) {
        if (cuenta == null) return;
        if (cuenta.getBankType() != BankType.BANCOLOMBIA) return; // el bot solo monitorea Bancolombia

        boolean estaActivaAhora = Boolean.TRUE.equals(cuenta.getActivaParaP2P());
        if (estaActivaAhora == estabaActivaAntes) return; // sin cambio, nada que avisar

        if (!autoSyncHabilitado) {
            log.info("[CuentaP2PSync] '{}' cambió de estado en P2P ({} → {}), pero autoSyncHabilitado=false "
                            + "— no se avisa a Movimientos (activa con app.cuentasp2p.auto-sync-habilitado=true).",
                    cuenta.getName(), estabaActivaAntes, estaActivaAhora);
            return;
        }

        if (estaActivaAhora) {
            log.info("[CuentaP2PSync] '{}' pasó a activa en P2P — encolando activación de monitoreo.",
                    cuenta.getName());
            conciliacionBancariaService.solicitarConciliacion(cuenta);
        } else {
            log.info("[CuentaP2PSync] '{}' dejó de estar activa en P2P — encolando detención de monitoreo.",
                    cuenta.getName());
            detencionService.solicitarDetencion(cuenta);
        }
    }
}
