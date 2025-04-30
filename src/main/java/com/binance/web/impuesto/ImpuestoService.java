package com.binance.web.impuesto;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.Impuesto;

@Service
public class ImpuestoService {
	
	@Autowired
    private ImpuestoRepository impuestoRepository;

    public List<Impuesto> findAll() {
        return impuestoRepository.findAll();
    }

    public Optional<Impuesto> findById(Integer id) {
        return impuestoRepository.findById(id);
    }

    public Impuesto save(Impuesto impuesto) {
        return impuestoRepository.save(impuesto);
    }

    public void deleteById(Integer id) {
        impuestoRepository.deleteById(id);
    }

}
