package com.binance.web.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

}
