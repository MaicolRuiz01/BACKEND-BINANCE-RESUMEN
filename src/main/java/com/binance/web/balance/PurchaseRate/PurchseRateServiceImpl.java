package com.binance.web.balance.PurchaseRate;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.PurchaseRate;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Repository.PurchaseRateRepository;
import com.binance.web.SaleP2P.SaleP2PService;
import com.binance.web.SellDollars.SellDollarsService;


@Service
public class PurchseRateServiceImpl implements PurchaseRateService {

    @Autowired
    private PurchaseRateRepository purchaseRateRepository;

    @Autowired
    private SaleP2PService saleP2PService;

    @Autowired
    private SellDollarsService sellDollarsService;

    @Override
    public void addPurchaseRate(BuyDollars lastBuy) {
    	PurchaseRate lastRate = purchaseRateRepository.findTopByOrderByDateDesc();
    	if (lastRate == null) {
    	    // crear una tasa inicial con valores default
    	    lastRate = new PurchaseRate();
    	    lastRate.setRate(0.0);
    	    lastRate.setDolares(0.0);
    	    lastRate.setDate(LocalDateTime.now());
    	}
        PurchaseRate newRate = new PurchaseRate();
        Double dolaresVendidos = 0.0;

        if (lastRate == null) {
            newRate.setDate(lastBuy.getDate());
            newRate.setDolares(lastBuy.getDollars());
            newRate.setRate(lastBuy.getTasa());
            purchaseRateRepository.save(newRate);
            return;
        }

        // Obtener ventas entre fechas y validar que no sean null
        List<SaleP2P> rangeSales = saleP2PService.obtenerVentasEntreFechas(lastRate.getDate(), lastBuy.getDate());
        if (rangeSales != null && !rangeSales.isEmpty()) {
            saleP2PService.saveUtilitydefinitive(rangeSales, lastRate.getRate());

            for (SaleP2P sale : rangeSales) {
                if (sale.getDollarsUs() == null) sale.setDollarsUs(0.0);
                dolaresVendidos += sale.getDollarsUs();
            }
        }

       

        // Cálculo de nueva tasa promedio
        Double dolaresSobrantes = lastRate.getDolares() - dolaresVendidos;
        Double totalPesos = (dolaresSobrantes * lastRate.getRate()) + lastBuy.getPesos();
        Double totalDolares = dolaresSobrantes + lastBuy.getDollars();
        Double averageRate = totalDolares == 0.0 ? lastRate.getRate() : totalPesos / totalDolares;

        newRate.setDate(lastBuy.getDate());
        newRate.setDolares(dolaresVendidos + lastBuy.getDollars());
        newRate.setRate(averageRate);
        purchaseRateRepository.save(newRate);
    }
}

