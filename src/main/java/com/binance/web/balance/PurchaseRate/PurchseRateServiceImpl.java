package com.binance.web.balance.PurchaseRate;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.BinanceAPI.BinanceService;
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

    @Autowired
    private BinanceService binanceService; // ⬅️ necesario para precios

    @Override
    public void addPurchaseRate(BuyDollars lastBuy) {
        // Última tasa guardada (puede ser null la primera vez)
        PurchaseRate lastRate = purchaseRateRepository.findTopByOrderByDateDesc();

        // === 1) Normalizamos la compra a USDT ===
        double buyUSDT = asUSDT(lastBuy.getAmount(), lastBuy.getCryptoSymbol());
        double buyCOP  = lastBuy.getPesos() != null ? lastBuy.getPesos() : 0.0;

        // Si no hay registro previo, iniciamos con la compra actual
        if (lastRate == null) {
            PurchaseRate init = new PurchaseRate();
            init.setDate(lastBuy.getDate());
            init.setDolares(buyUSDT);
            // si envías tasa explícita úsala, si no calcula pesos/USDT (evita /0)
            double rate = (lastBuy.getTasa() != null && lastBuy.getTasa() > 0)
                    ? lastBuy.getTasa()
                    : (buyUSDT > 0 ? buyCOP / buyUSDT : 0.0);
            init.setRate(rate);
            purchaseRateRepository.save(init);
            return;
        }

        // === 2) Ventas en el rango (desde la última tasa hasta esta compra) ===
        double dolaresVendidos = 0.0;
        List<SaleP2P> rangeSales =
                saleP2PService.obtenerVentasEntreFechas(lastRate.getDate(), lastBuy.getDate());

        if (rangeSales != null && !rangeSales.isEmpty()) {
            // Mantienes tu lógica de utilidades
            saleP2PService.saveUtilitydefinitive(rangeSales, lastRate.getRate());

            for (SaleP2P sale : rangeSales) {
                // asumo que SaleP2P.getDollarsUs() ya está en USDT; si no, conviértelo igual que la compra
                Double d = sale.getDollarsUs();
                dolaresVendidos += (d != null ? d : 0.0);
            }
        }

        // === 3) Promedio ponderado ===
        double dolaresSobrantes = (lastRate.getDolares() != null ? lastRate.getDolares() : 0.0) - dolaresVendidos;
        if (dolaresSobrantes < 0) dolaresSobrantes = 0.0;

        double totalPesos   = (dolaresSobrantes * lastRate.getRate()) + buyCOP;
        double totalDolares = dolaresSobrantes + buyUSDT;

        double averageRate =
                (totalDolares > 0.0) ? (totalPesos / totalDolares) : lastRate.getRate();

        // === 4) Guardar nueva tasa ===
        PurchaseRate newRate = new PurchaseRate();
        newRate.setDate(lastBuy.getDate());
        // ⚠️ importante: guardar inventario post-compra, no “vendidos + compra”
        newRate.setDolares(totalDolares);
        newRate.setRate(averageRate);

        purchaseRateRepository.save(newRate);
    }

    /**
     * Convierte una cantidad de cualquier cripto a su equivalente en USDT.
     * USDT/USDC valen 1.0.
     */
    private double asUSDT(Double amount, String symbol) {
        if (amount == null || amount <= 0) return 0.0;
        if (symbol == null) return amount; // asumimos que ya está en USDT
        String s = symbol.trim().toUpperCase();
        if ("USDT".equals(s) || "USDC".equals(s)) return amount;

        try {
            Double px = binanceService.getPriceInUSDT(s);
            return (px != null && px > 0) ? amount * px : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}

