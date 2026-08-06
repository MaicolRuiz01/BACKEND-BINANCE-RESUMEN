package com.binance.web.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.CuentaAnuncioEstado;

public interface CuentaAnuncioEstadoRepository extends JpaRepository<CuentaAnuncioEstado, Integer> {

    Optional<CuentaAnuncioEstado> findByVendedor(String vendedor);
}
