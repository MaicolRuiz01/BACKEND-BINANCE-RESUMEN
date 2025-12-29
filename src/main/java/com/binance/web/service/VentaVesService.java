package com.binance.web.service;

import java.time.LocalDate;
import java.util.List;

import com.binance.web.Entity.VentaVES;

public interface VentaVesService {
	VentaVES create(VentaVES venta);
    VentaVES update(Long id, VentaVES venta);
    void delete(Long id);
    VentaVES findById(Long id);
    List<VentaVES> findAll();
    List<VentaVES> findByDay(LocalDate day);

}
