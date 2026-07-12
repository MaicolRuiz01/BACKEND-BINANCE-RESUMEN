package com.binance.web.transacciones;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.Transacciones;

@RestController
@RequestMapping("/transacciones")
public class TransaccionesController {
	private final TransaccionesService transaccionesService;
	private final TraspasoReconciliacionService reconciliacionService;
	private final TransaccionesRepository transaccionesRepository;

    public TransaccionesController(TransaccionesService transaccionesService,
                                  TraspasoReconciliacionService reconciliacionService,
                                  TransaccionesRepository transaccionesRepository) {
        this.transaccionesService = transaccionesService;
        this.reconciliacionService = reconciliacionService;
        this.transaccionesRepository = transaccionesRepository;
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
        // Convierte compras/ventas sin asignar que son traspasos internos (Binance↔TRON)
        // antes de devolver la lista, para que aparezcan como traspasos.
        try { reconciliacionService.reconciliarTraspasos(); } catch (Exception ignore) {}
        List<TransaccionesDTO> result = transaccionesService.saveAndFetchTodayTraspasos();
        return ResponseEntity.ok(result);
    }

    /**
     * Listado paginado y liviano de TODOS los traspasos, para la vista de Asignar → Traspasos.
     * Devuelve solo lo que pinta la tabla (de, a, fecha, cantidad, moneda) → carga rápida.
     * Ej: GET /transacciones/listado?page=0&size=20
     */
    @GetMapping("/listado")
    public ResponseEntity<Page<TransaccionesRepository.TraspasoListItem>> listarTraspasos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = (size <= 0 || size > 200) ? 20 : size;
        int safePage = Math.max(page, 0);
        Page<TransaccionesRepository.TraspasoListItem> result =
                transaccionesRepository.findTraspasosPaginados(PageRequest.of(safePage, safeSize));
        return ResponseEntity.ok(result);
    }

    /** Reconciliación manual de traspasos internos (compra+venta del mismo monto y hora). */
    @PostMapping("/reconciliar-traspasos")
    public ResponseEntity<?> reconciliarTraspasos() {
        int n = reconciliacionService.reconciliarTraspasos();
        return ResponseEntity.ok(Map.of("emparejados", n));
    }

}
