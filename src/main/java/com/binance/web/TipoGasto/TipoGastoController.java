package com.binance.web.TipoGasto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("api/tipo-gasto")
public class TipoGastoController {

    @Autowired
    private TipoGastoService tipoGastoService;

    @GetMapping
    public List<TipoGasto> listarTodos() {
        return tipoGastoService.listarTodos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoGasto> obtenerPorId(@PathVariable Integer id) {
        Optional<TipoGasto> tipoGasto = tipoGastoService.obtenerPorId(id);
        return tipoGasto.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public TipoGasto crear(@RequestBody TipoGasto tipoGasto) {
        return tipoGastoService.crear(tipoGasto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TipoGasto> actualizar(@PathVariable Integer id, @RequestBody TipoGasto tipoGastoDetalles) {
        return tipoGastoService.actualizar(id, tipoGastoDetalles)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        return tipoGastoService.eliminar(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
