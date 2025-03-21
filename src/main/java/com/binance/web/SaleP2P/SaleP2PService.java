package com.binance.web.SaleP2P;

import java.util.List;

public interface SaleP2PService {
	List<SaleP2P> findAllSaleP2P();
    SaleP2P findByIdSaleP2P(Integer id);
    void saveSaleP2P(SaleP2P saleP2P);
    void updateSaleP2P(Integer id, SaleP2P sale);
    void deleteSaleP2P(Integer id);
    void processAssignAccountCop(SaleP2PDto sale);
}
