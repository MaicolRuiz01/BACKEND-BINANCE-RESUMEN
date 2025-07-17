package com.binance.web.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.binance.web.Entity.Efectivo;

public interface EfectivoRepository extends JpaRepository<Efectivo, Integer>{
	
	Efectivo findByName(String name);

}
