package com.binance.web.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.binance.web.Entity.VesAverageRate;

public interface VesAverageRateService {

    VesAverageRate getUltima();

    VesAverageRate inicializarTasaInicial(Double tasaInicialCopPorVes, LocalDateTime fecha);

    VesAverageRate actualizarPorCompra(
            Double cantidadVesComprada,
            Double totalPesosCompra,
            LocalDateTime fechaOperacion
    );

    List<VesAverageRate> listarPorDia(LocalDate dia);
}

