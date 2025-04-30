package com.binance.web.impuesto;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.Impuesto;

public interface ImpuestoRepository extends JpaRepository<Impuesto, Integer> {

}
