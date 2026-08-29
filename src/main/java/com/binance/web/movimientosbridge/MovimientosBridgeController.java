package com.binance.web.movimientosbridge;

import com.binance.web.activacion.ActivacionResultadoDto;
import com.binance.web.activacion.ActivacionService;
import com.binance.web.detencion.DetencionResultadoDto;
import com.binance.web.detencion.DetencionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints que llaman los scripts de Movimientos/Pochonance (Automatizacion
 * Bancolombia / Movimientos/Pochonance/). Igual que /conciliacion/**, son
 * scripts corriendo en el computador de Milton, no un usuario logueado — se
 * autentican con una API key fija (header X-Bot-Api-Key) y las rutas están
 * marcadas como públicas en SecurityConfig; la validación de la key se hace
 * a mano acá.
 *
 * - POST /evento               → pochonance_bridge.py (movimientos, cambios de
 *   saldo, y el resultado del primer login de una cuenta recién activada).
 * - GET  /activacion/pendiente
 * - POST /activacion/resultado → pochonance_activador.py (arrancar el
 *   monitoreo de una cuenta activada en "Cuentas P2P"). Ver ActivacionService
 *   para el contrato completo y por qué es polling, no un mensaje de Telegram.
 */
@Slf4j
@RestController
@RequestMapping("/movimientos")
@RequiredArgsConstructor
public class MovimientosBridgeController {

    private final MovimientosBridgeService movimientosBridgeService;
    private final ActivacionService activacionService;
    private final DetencionService detencionService;

    @Value("${app.cuentasp2p.bridge-api-key:}")
    private String apiKeyEsperada;

    private ResponseEntity<Map<String, String>> _errorSiApiKeyInvalida(String apiKeyRecibida) {
        if (apiKeyEsperada == null || apiKeyEsperada.isBlank()) {
            log.error("[CuentasP2P Bridge] app.cuentasp2p.bridge-api-key no configurada en el servidor — rechazando todo por seguridad.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "El servidor no tiene configurada la API key del bridge."));
        }
        if (apiKeyRecibida == null || !apiKeyRecibida.equals(apiKeyEsperada)) {
            log.warn("[CuentasP2P Bridge] Intento de acceso con API key inválida o ausente.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "API key inválida o ausente (header X-Bot-Api-Key)."));
        }
        return null;
    }

    @PostMapping("/evento")
    public ResponseEntity<?> recibirEvento(
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKeyRecibida,
            @RequestBody MovimientoEventoDto evento) {

        ResponseEntity<Map<String, String>> err = _errorSiApiKeyInvalida(apiKeyRecibida);
        if (err != null) return err;

        movimientosBridgeService.procesarEvento(evento);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * El bot (pochonance_activador.py) llama esto en cada ciclo de polling
     * para preguntar si hay una cuenta pendiente por activar. Devuelve 200
     * con {"cuenta": nombre} y la marca como consumida, o 204 si no hay nada.
     */
    @GetMapping("/activacion/pendiente")
    public ResponseEntity<?> obtenerActivacionPendiente(
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKeyRecibida) {

        ResponseEntity<Map<String, String>> err = _errorSiApiKeyInvalida(apiKeyRecibida);
        if (err != null) return err;

        return activacionService.obtenerYConsumirPendiente()
                .<ResponseEntity<?>>map(cuenta -> ResponseEntity.ok(Map.of("cuenta", cuenta)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * El bot llama esto justo después de intentar arrancar (o no) el
     * monitoreo de la cuenta que le llegó por /activacion/pendiente.
     */
    @PostMapping("/activacion/resultado")
    public ResponseEntity<?> recibirActivacionResultado(
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKeyRecibida,
            @RequestBody ActivacionResultadoDto resultado) {

        ResponseEntity<Map<String, String>> err = _errorSiApiKeyInvalida(apiKeyRecibida);
        if (err != null) return err;

        activacionService.procesarResultado(resultado);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * El bot llama esto en cada ciclo de polling para preguntar si hay una
     * cuenta pendiente por DETENER (dejó de estar seleccionada en P2P — ver
     * CuentaP2PSyncService). Devuelve 200 con {"cuenta": nombre} y la marca
     * como consumida, o 204 si no hay nada.
     */
    @GetMapping("/detencion/pendiente")
    public ResponseEntity<?> obtenerDetencionPendiente(
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKeyRecibida) {

        ResponseEntity<Map<String, String>> err = _errorSiApiKeyInvalida(apiKeyRecibida);
        if (err != null) return err;

        return detencionService.obtenerYConsumirPendiente()
                .<ResponseEntity<?>>map(cuenta -> ResponseEntity.ok(Map.of("cuenta", cuenta)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * El bot llama esto justo después de intentar parar (o no) el monitoreo
     * de la cuenta que le llegó por /detencion/pendiente.
     */
    @PostMapping("/detencion/resultado")
    public ResponseEntity<?> recibirDetencionResultado(
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKeyRecibida,
            @RequestBody DetencionResultadoDto resultado) {

        ResponseEntity<Map<String, String>> err = _errorSiApiKeyInvalida(apiKeyRecibida);
        if (err != null) return err;

        detencionService.procesarResultado(resultado);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
