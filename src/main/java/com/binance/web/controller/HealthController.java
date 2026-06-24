package com.binance.web.controller;

import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.RetiradorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * HealthController - Endpoint para verificar el estado del backend y la conexión a la BD
 * 
 * Propósito: Diagnosticar problemas de conexión entre el frontend y backend
 * Endpoints:
 *   GET /api/health              - Estado general del backend
 *   GET /api/health/db           - Información detallada de BD
 *   GET /api/health/cajas        - Listado de cajas y retiradores
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthController {
    
    @Autowired
    private EfectivoRepository efectivoRepository;
    
    @Autowired
    private RetiradorRepository retiradorRepository;
    
    /**
     * Endpoint de health check básico
     * Verifica que el backend está corriendo y conectado a la BD
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            long cajasCount = efectivoRepository.count();
            long retiradorasCount = retiradorRepository.count();
            
            response.put("status", "UP ✅");
            response.put("database", "CONNECTED ✅");
            response.put("cajas_count", cajasCount);
            response.put("retiradores_count", retiradorasCount);
            response.put("timestamp", LocalDateTime.now());
            response.put("message", "Backend en funcionamiento correcto - Conexión a BD exitosa");
            
            log.info("Health check - Cajas: {}, Retiradores: {}", cajasCount, retiradorasCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error en health check", e);
            
            response.put("status", "DOWN ❌");
            response.put("database", "DISCONNECTED ❌");
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            response.put("message", "Error conectando a la base de datos");
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Información detallada de la BD
     * Muestra todas las cajas y retiradores registrados
     */
    @GetMapping("/db")
    public ResponseEntity<Map<String, Object>> dbInfo() {
        try {
            var cajas = efectivoRepository.findAll();
            var retiradores = retiradorRepository.findAll();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "OK ✅");
            response.put("cajas", cajas);
            response.put("retiradores", retiradores);
            response.put("cajas_count", cajas.size());
            response.put("retiradores_count", retiradores.size());
            response.put("timestamp", LocalDateTime.now());
            
            log.info("DB Info - Cajas: {}, Retiradores: {}", cajas.size(), retiradores.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error obteniendo información de BD", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ERROR ❌");
            response.put("error", "Error al obtener información: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Endpoint para diagnosticar el problema específico de cajas
     * Verifica si hay cajas disponibles para crear retiros
     */
    @GetMapping("/cajas")
    public ResponseEntity<Map<String, Object>> cajasInfo() {
        try {
            var cajas = efectivoRepository.findAll();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "OK ✅");
            response.put("cajas_disponibles", cajas.size());
            response.put("cajas", cajas);
            response.put("timestamp", LocalDateTime.now());
            
            if (cajas.isEmpty()) {
                response.put("warning", "⚠️ No hay cajas registradas. El sistema no puede crear retiros sin cajas.");
                response.put("recomendacion", "1. Crear una caja: POST /efectivo { name: 'Caja 1', saldo: 0 }");
            }
            
            log.info("Cajas Info - Total: {}", cajas.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error obteniendo cajas", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "ERROR ❌");
            response.put("error", "Error al obtener cajas: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Test de conexión simple
     * Solo verifica si la BD responde
     */
    @GetMapping("/test-connection")
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Intentar ejecutar query simple
            long count = efectivoRepository.count();
            
            response.put("status", "CONNECTED ✅");
            response.put("connection", "SUCCESS");
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Fallo en test de conexión", e);
            
            response.put("status", "DISCONNECTED ❌");
            response.put("connection", "FAILED");
            response.put("error", e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}
