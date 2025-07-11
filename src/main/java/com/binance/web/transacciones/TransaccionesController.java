package com.binance.web.transacciones;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.Transacciones;

@RestController
@RequestMapping("/transacciones")
public class TransaccionesController {
	private final TransaccionesService transaccionesService;

    public TransaccionesController(TransaccionesService transaccionesService) {
        this.transaccionesService = transaccionesService;
    }

    @PostMapping("/guardar")
    public ResponseEntity<?> guardarTransaccion(@RequestBody TransaccionesDTO dto) {
        try {
            Transacciones guardada = transaccionesService.guardarTransaccion(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(guardada);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno"));
        }
    }
    
    @GetMapping("/hoy")
    public ResponseEntity<List<TransaccionesDTO>> saveAndFetchToday() {
        List<TransaccionesDTO> result = transaccionesService.saveAndFetchTodayTraspasos();
        return ResponseEntity.ok(result);
    }

}
