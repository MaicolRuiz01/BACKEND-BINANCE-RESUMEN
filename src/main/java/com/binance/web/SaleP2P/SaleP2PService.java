package com.binance.web.SaleP2P;

import java.util.List;

import com.binance.web.Entity.SaleP2P;

public interface SaleP2PService {
	List<SaleP2P> findAllSaleP2P();
	List<SaleP2PDto> getLastSaleP2pToday(String account);
    SaleP2P findByIdSaleP2P(Integer id);
    void saveSaleP2P(SaleP2P saleP2P);
    void updateSaleP2P(Integer id, SaleP2P sale);
    void deleteSaleP2P(Integer id);
    void processAssignAccountCop(SaleP2PDto sale);
}
