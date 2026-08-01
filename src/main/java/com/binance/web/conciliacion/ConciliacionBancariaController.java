package com.binance.web.conciliacion;

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
 * Endpoint que llama el bot de conciliación bancaria (Automatizacion
 * Bancolombia / conciliacion_bancaria.py), un script que corre en el
 * computador de Milton, NO un usuario logueado en el navegador. Por eso no
 * usa el login/JWT normal de la app — se autentica con una API key fija
 * (header X-Bot-Api-Key) configurada en application-prod.properties, y la
 * ruta está marcada como pública en SecurityConfig (el filtro JWT no aplica
 * aquí; la validación de la key se hace a mano abajo).
 */
@Slf4j
@RestController
@RequestMapping("/conciliacion")
public class ConciliacionBancariaController {

    private final ConciliacionBancariaService conciliacionBancariaService;

    @Value("${app.conciliacion.api-key:}")
    private String apiKeyEsperada;

    public ConciliacionBancariaController(ConciliacionBancariaService conciliacionBancariaService) {
        this.conciliacionBancariaService = conciliacionBancariaService;
    }

    @PostMapping("/resultado")
    public ResponseEntity<?> recibirResultado(
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKeyRecibida,
            @RequestBody ConciliacionResultadoDto request) {

        if (apiKeyEsperada == null || apiKeyEsperada.isBlank()) {
            log.error("[Conciliacion] app.conciliacion.api-key no configurada en el servidor — rechazando todo por seguridad.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "El servidor no tiene configurada la API key de conciliación."));
        }
        if (apiKeyRecibida == null || !apiKeyRecibida.equals(apiKeyEsperada)) {
            log.warn("[Conciliacion] Intento de acceso con API key inválida o ausente.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "API key inválida o ausente (header X-Bot-Api-Key)."));
        }

        ConciliacionResponseDto response = conciliacionBancariaService.procesarResultado(request);
        log.info("[Conciliacion] Procesado: {} actualizada(s), {} no encontrada(s): {}",
                response.getActualizados().size(), response.getNoEncontrados().size(), response.getNoEncontrados());
        return ResponseEntity.ok(response);
    }

    /**
     * El bot de conciliación llama esto una sola vez (o cada vez que arranca)
     * para decirle a Pochonance a qué chat de Telegram mandarle avisos —
     * necesario para el "auto-trigger": cuando se activa una cuenta
     * Bancolombia en "Cuentas P2P", Pochonance le manda un mensaje a ESTE
     * chat pidiéndole al bot que la revise ya mismo (ver
     * AccountCopController.toggleActivaParaP2P y
     * ConciliacionBancariaServiceImpl.solicitarConciliacion).
     */
    @PostMapping("/registrar-chat")
    public ResponseEntity<?> registrarChat(
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKeyRecibida,
            @RequestBody RegistrarChatDto request) {

        if (apiKeyEsperada == null || apiKeyEsperada.isBlank()) {
            log.error("[Conciliacion] app.conciliacion.api-key no configurada en el servidor — rechazando todo por seguridad.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "El servidor no tiene configurada la API key de conciliación."));
        }
        if (apiKeyRecibida == null || !apiKeyRecibida.equals(apiKeyEsperada)) {
            log.warn("[Conciliacion] Intento de acceso con API key inválida o ausente.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "API key inválida o ausente (header X-Bot-Api-Key)."));
        }
        if (request == null || request.getChatId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Falta chatId en el cuerpo."));
        }

        conciliacionBancariaService.registrarChat(request.getChatId());
        log.info("[Conciliacion] Chat de Telegram del bot registrado: {}", request.getChatId());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * El bot llama esto en cada ciclo de polling (en vez de esperar un
     * mensaje de Telegram, que nunca le podría llegar — ver
     * ConciliacionBancariaServiceImpl.solicitarConciliacion) para preguntar
     * si hay una cuenta pendiente por conciliar. Devuelve 200 con
     * {"cuenta": nombre} y la marca como consumida, o 204 si no hay nada
     * pendiente en este momento.
     */
    @GetMapping("/pendiente")
    public ResponseEntity<?> obtenerPendiente(
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKeyRecibida) {

        if (apiKeyEsperada == null || apiKeyEsperada.isBlank()) {
            log.error("[Conciliacion] app.conciliacion.api-key no configurada en el servidor — rechazando todo por seguridad.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "El servidor no tiene configurada la API key de conciliación."));
        }
        if (apiKeyRecibida == null || !apiKeyRecibida.equals(apiKeyEsperada)) {
            log.warn("[Conciliacion] Intento de acceso con API key inválida o ausente.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "API key inválida o ausente (header X-Bot-Api-Key)."));
        }

        return conciliacionBancariaService.obtenerYConsumirPendiente()
                .<ResponseEntity<?>>map(cuenta -> ResponseEntity.ok(Map.of("cuenta", cuenta)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
