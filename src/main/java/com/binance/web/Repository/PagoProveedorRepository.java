package com.binance.web.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.binance.web.Entity.PagoProveedor;

@Repository
public interface PagoProveedorRepository extends JpaRepository<PagoProveedor, Integer> {
	List<PagoProveedor> findBySupplierId(Integer supplierId);

}
