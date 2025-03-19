package com.binance.web.service;

import java.util.List;

import com.binance.web.entity.SaleP2P;

public interface SaleP2PService {
	List<SaleP2P> findAllSaleP2P();
    SaleP2P findByIdSaleP2P(Integer id);
    void saveSaleP2P(SaleP2P saleP2P);
    void updateSaleP2P(Integer id, SaleP2P sale);
    void deleteSaleP2P(Integer id);
}
