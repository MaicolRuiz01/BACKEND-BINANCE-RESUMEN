package com.binance.web.service;

import java.util.List;

import com.binance.web.model.DeduccionDto;

public interface DeduccionService {

    List<DeduccionDto> listar();

    DeduccionDto crear(DeduccionDto dto);

    DeduccionDto actualizar(Integer id, DeduccionDto dto);

    void eliminar(Integer id);
}
