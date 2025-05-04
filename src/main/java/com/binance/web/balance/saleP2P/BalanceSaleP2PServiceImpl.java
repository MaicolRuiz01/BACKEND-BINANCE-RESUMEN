package com.binance.web.balance.saleP2P;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.SaleP2P.SaleP2P;
import com.binance.web.SaleP2P.SaleP2PRepository;

@Service
public class BalanceSaleP2PServiceImpl implements BalanceSaleP2PService{
	
	@Autowired
	private SaleP2PRepository p2pRepository;

	public BalanceSaleP2PDto balanceSaleP2PDay(Date fehca) {
		List<SaleP2P> daySales = generateListSaleP2PDay(fehca);
		BalanceSaleP2PDto  balanceSaleP2P = createBalanceSaleP2PDto(daySales);
		
		return balanceSaleP2P;
	}
	
	private List<SaleP2P> generateListSaleP2PDay(Date fehca) {
		return p2pRepository.findByDate(fehca);
	}
	
	private BalanceSaleP2PDto createBalanceSaleP2PDto(List<SaleP2P> daySales) {
        BalanceSaleP2PDto balanceSaleP2P = new BalanceSaleP2PDto();
        
        // Inicializamos los campos a 0.0 para evitar NullPointerException
        balanceSaleP2P.setVendidos(0.0);
        balanceSaleP2P.setComisionUsdt(0.0);
        balanceSaleP2P.setImpuestosCol(0.0);
        
        for (SaleP2P saleP2P : daySales) {
            double pesos = saleP2P.getPesosCop() != null ? saleP2P.getPesosCop() : 0.0;
            double com = saleP2P.getCommission() != null ? saleP2P.getCommission() : 0.0;

            balanceSaleP2P.setVendidos(balanceSaleP2P.getVendidos() + pesos);
            balanceSaleP2P.setComisionUsdt(balanceSaleP2P.getComisionUsdt() + com);
            balanceSaleP2P.setImpuestosCol(balanceSaleP2P.getImpuestosCol() + (pesos * 0.004));
        }
        
        return balanceSaleP2P;
    }
}
