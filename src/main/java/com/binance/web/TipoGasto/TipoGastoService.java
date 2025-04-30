package com.binance.web.TipoGasto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.TipoGasto;

import java.util.List;
import java.util.Optional;

@Service
public class TipoGastoService {

    @Autowired
    private TipoGastoRepository tipoGastoRepository;

    public List<TipoGasto> listarTodos() {
        return tipoGastoRepository.findAll();
    }

    public Optional<TipoGasto> obtenerPorId(Integer id) {
        return tipoGastoRepository.findById(id);
    }

    public TipoGasto crear(TipoGasto tipoGasto) {
        return tipoGastoRepository.save(tipoGasto);
    }

    public Optional<TipoGasto> actualizar(Integer id, TipoGasto tipoGastoDetalles) {
        return tipoGastoRepository.findById(id).map(tipoGasto -> {
            tipoGasto.setDescripcion(tipoGastoDetalles.getDescripcion());
            return tipoGastoRepository.save(tipoGasto);
        });
    }

    public boolean eliminar(Integer id) {
        return tipoGastoRepository.findById(id).map(tipoGasto -> {
            tipoGastoRepository.delete(tipoGasto);
            return true;
        }).orElse(false);
    }
    
    
}
