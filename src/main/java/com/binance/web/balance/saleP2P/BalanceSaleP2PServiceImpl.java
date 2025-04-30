package com.binance.web.balance.saleP2P;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.SaleP2P;
import com.binance.web.SaleP2P.SaleP2PRepository;

@Service
public class BalanceSaleP2PServiceImpl {
	
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
		BalanceSaleP2PDto  balanceSaleP2P = new BalanceSaleP2PDto();
		for (SaleP2P saleP2P : daySales) {
			balanceSaleP2P.setVendidos(balanceSaleP2P.getVendidos() + saleP2P.getPesosCop());
			balanceSaleP2P.setComisionUsdt(balanceSaleP2P.getComisionUsdt() + saleP2P.getCommission());
			balanceSaleP2P.setImpuestosCol(balanceSaleP2P.getImpuestosCol() + ( saleP2P.getPesosCop() * 0.004));
		}
		return balanceSaleP2P;
	}
}
