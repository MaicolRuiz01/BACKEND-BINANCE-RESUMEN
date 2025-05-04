package com.binance.web.balance.saleP2P;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SaleP2PRepository;

@Service
public class BalanceSaleP2PServiceImpl {
	
	@Autowired
	private SaleP2PRepository p2pRepository;
	
	@Autowired
	private BuyDollarsRepository buyDollarsRepository;

	public BalanceSaleP2PDto balanceSaleP2PDay(Date fecha) {
		List<SaleP2P> daySales = generateListSaleP2PDay(fecha);
		BalanceSaleP2PDto  balanceSaleP2P = createBalanceSaleP2PDto(daySales);
		
		return balanceSaleP2P;
	}
	
	private List<SaleP2P> generateListSaleP2PDay(Date fecha) {
		return p2pRepository.findByDate(fecha);
	}
	
	private BalanceSaleP2PDto createBalanceSaleP2PDto(List<SaleP2P> daySales) {
		BalanceSaleP2PDto  balanceSaleP2P = new BalanceSaleP2PDto();
		Double vendidos = 0.0;
		Double	comision = 0.0;
		Double	impuestos = 0.0;
		Double dolares = 0.0;
		
		for (SaleP2P saleP2P : daySales) {
			vendidos += saleP2P.getPesosCop();
			comision += saleP2P.getCommission();
			impuestos += ( saleP2P.getPesosCop() * 0.004);
			dolares += saleP2P.getDollarsUs();
		}
		balanceSaleP2P.setVendidos(vendidos);
		balanceSaleP2P.setComisionUsdt(comision);
		balanceSaleP2P.setImpuestosCol(impuestos);
		balanceSaleP2P.setTasaVenta(vendidos / dolares);
		return balanceSaleP2P;
	}
	
	private List<BuyDollars> generateListBuyDollarsDay(Date fecha) {
		return buyDollarsRepository.findByDate(fecha);
	}
}
