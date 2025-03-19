package com.binance.web.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import com.binance.web.entity.SaleP2P;
import com.binance.web.repository.SaleP2PRepository;
import com.binance.web.service.SaleP2PService;

@Service
public class SaleP2PServiceImpl implements SaleP2PService{
	
	private final SaleP2PRepository saleP2PRepository;
	
	public SaleP2PServiceImpl(SaleP2PRepository saleP2PRepository) {
	    this.saleP2PRepository = saleP2PRepository;
	}
	
	public List<SaleP2P> findAllSaleP2P() {
		List<SaleP2P> salesP2P = saleP2PRepository.findAll();
		return salesP2P;
	}
	
	public SaleP2P findByIdSaleP2P(Integer id) {
		SaleP2P saleP2P = saleP2PRepository.findById(id).get();
		return saleP2P;
	}
	
	public void saveSaleP2P(SaleP2P saleP2P) {
		saleP2PRepository.save(saleP2P);
	}
	
	public void updateSaleP2P(Integer id, SaleP2P sale) {
		SaleP2P saleP2P = saleP2PRepository.findById(id).orElse(null);
		saleP2PRepository.save(saleP2P);
	}
	
	public void deleteSaleP2P(Integer id) {
		saleP2PRepository.deleteById(id);
	}

}
