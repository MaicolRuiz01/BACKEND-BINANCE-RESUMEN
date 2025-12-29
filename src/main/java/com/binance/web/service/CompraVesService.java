package com.binance.web.service;

import java.time.LocalDate;
import java.util.List;

import com.binance.web.Entity.CompraVES;

public interface CompraVesService {
	CompraVES create(CompraVES compra);
    CompraVES update(Long id, CompraVES compra);
    void delete(Long id);
    CompraVES findById(Long id);
    List<CompraVES> findAll();
    List<CompraVES> findByDay(LocalDate day);
}
