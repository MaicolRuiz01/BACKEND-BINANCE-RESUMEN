package com.binance.web.gastos;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.Gasto;

import java.util.List;
import java.util.Optional;

@Service
public class GastoService {

    @Autowired
    private GastoRepository gastoRepository;

    public List<Gasto> findAll() {
        return gastoRepository.findAll();
    }

    public Optional<Gasto> findById(Integer id) {
        return gastoRepository.findById(id);
    }

    public Gasto save(Gasto gasto) {
        return gastoRepository.save(gasto);
    }

    public void deleteById(Integer id) {
        gastoRepository.deleteById(id);
    }
    
    public Optional<Gasto> marcarComoPagado(Integer id) {
        return gastoRepository.findById(id).map(gasto -> {
            gasto.setPagado(true);
            return gastoRepository.save(gasto);
        });
    }

}
