package com.binance.web.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Manejador global de excepciones.
 *
 * Sin esto, cuando un servicio lanza una excepción (ej: "Saldo insuficiente..."),
 * Spring Boot la resuelve con un FORWARD interno a /error. Ese forward no pasa
 * por el filtro de CORS de Spring Security de la misma manera que la petición
 * original, así que el navegador termina bloqueando la lectura del cuerpo de la
 * respuesta de error (aunque el backend sí mandó el mensaje). El resultado en el
 * frontend: el toast siempre muestra el mensaje genérico de fallback en vez del
 * motivo real ("Saldo insuficiente...", "Ya fue confirmada", etc).
 *
 * Al capturar la excepción acá y devolver el ResponseEntity directamente (sin
 * forward), la respuesta sale por el mismo camino que cualquier respuesta normal
 * y sí lleva los headers de CORS — el frontend puede leer err.error.message
 * siempre.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        // La mayoría de los "no encontrado" del proyecto se lanzan como RuntimeException genérica,
        // pero también caen acá excepciones inesperadas de Hibernate/JPA (ej. TransientObjectException)
        // que sí necesitan el stacktrace completo para diagnosticarse — con solo el mensaje no alcanza.
        log.error("[GlobalExceptionHandler] RuntimeException no específica: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Ocurrió un error inesperado."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAny(Exception ex) {
        log.error("[GlobalExceptionHandler] Error no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Ocurrió un error inesperado en el servidor."));
    }
}
