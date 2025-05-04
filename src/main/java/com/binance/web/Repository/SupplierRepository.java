package com.binance.web.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.Supplier;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Integer>{
	Supplier findByName(String name);
}
