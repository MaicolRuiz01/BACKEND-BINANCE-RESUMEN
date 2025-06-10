package com.binance.web.balance.PurchaseRate;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.Balance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.PurchaseRate;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Repository.PurchaseRateRepository;
import com.binance.web.SaleP2P.SaleP2PService;

@Service
public class PurchseRateServiceImpl implements PurchaseRateService {

	@Autowired
	private PurchaseRateRepository purchaseRateRepository;

	@Autowired
	private SaleP2PService saleP2PService;

	@Override
	public void createPurchaseRate(Balance balance) {
		Double purchaseRate = balance.getPesos() / balance.getUsdt();
		PurchaseRate tasaCompra = new PurchaseRate();
		tasaCompra.setDate(balance.getDate());
		tasaCompra.setDolares(balance.getUsdt());
		tasaCompra.setRate(purchaseRate);
		purchaseRateRepository.save(tasaCompra);
	}

	@Override
	public void addPurchaseRate(BuyDollars lastBuy) {
		PurchaseRate lastRate = purchaseRateRepository.findTopByOrderByDateDesc();
		PurchaseRate newRate = new PurchaseRate();
		Double dolaresVendidos = 0.0;

		if (lastRate == null) {
			newRate.setDate(lastBuy.getDate());
			newRate.setDolares(lastBuy.getDollars());
			newRate.setRate(lastBuy.getTasa());
			purchaseRateRepository.save(newRate);
			return;
		}

		List<SaleP2P> rangeSales = saleP2PService.obtenerVentasEntreFechas(lastRate.getDate(), lastBuy.getDate());
		saleP2PService.saveUtilitydefinitive(rangeSales, lastRate.getRate());

		for (SaleP2P sale : rangeSales) {
			dolaresVendidos += sale.getDollarsUs();
		}
		Double dolaresSobrantes = lastRate.getDolares() - dolaresVendidos;
		Double averageRate = ((dolaresSobrantes * lastRate.getRate()) + lastBuy.getPesos())
				/ (dolaresSobrantes + lastBuy.getDollars());

		newRate.setDate(lastBuy.getDate());
		newRate.setDolares(dolaresVendidos + lastBuy.getDollars());
		newRate.setRate(averageRate);
		purchaseRateRepository.save(newRate);
	}
}
