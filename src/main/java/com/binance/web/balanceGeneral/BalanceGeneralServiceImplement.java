package com.binance.web.balanceGeneral;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.SaleP2P;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.BalanceGeneralRepository;
import com.binance.web.Repository.PurchaseRateRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.SaleP2P.SaleP2PService;
import com.binance.web.SellDollars.SellDollarsService;

import jakarta.transaction.Transactional;
@Service
public class BalanceGeneralServiceImplement implements BalanceGeneralService{
	
	@Autowired
    private BalanceGeneralRepository balanceRepo;
    @Autowired
    private AccountBinanceRepository accountBinanceRepo;
    @Autowired
    private AccountCopRepository accountCopRepo;
    @Autowired
    private SupplierRepository accountProveedorRepo;
    @Autowired
    private PurchaseRateRepository rateRepo;
    @Autowired
    private SaleP2PService saleP2PService;
    @Autowired
    private SellDollarsService sellDollarsService;

    @Override
    @Transactional
    public void calcularOBalancear(LocalDate fecha) {
        Double tasaCompra = rateRepo.findTopByOrderByDateDesc().getRate();

        Double saldoBinance = accountBinanceRepo.findAll().stream()
            .mapToDouble(a -> a.getBalance() * tasaCompra)
            .sum();

        Double saldoCop = accountCopRepo.findAll().stream()
            .mapToDouble(a -> a.getBalance())
            .sum();

        Double saldoProveedores = accountProveedorRepo.findAll().stream()
            .mapToDouble(a -> a.getBalance())
            .sum();

        Double saldoTotal = saldoBinance + saldoCop - saldoProveedores;

        Double totalP2P = saleP2PService.obtenerVentasPorFecha(fecha).stream()
        	    .mapToDouble(SaleP2P::getPesosCop)
        	    .sum();


        Double comisionesP2P = saleP2PService.obtenerComisionesPorFecha(fecha);

        Double totalGeneralSales = sellDollarsService.obtenerVentasPorFecha(fecha).stream()
            .mapToDouble(v -> v.getPesos())
            .sum();

        Double totalUsdt = saleP2PService.obtenerVentasPorFecha(fecha).stream()
            .mapToDouble(v -> v.getDollarsUs())
            .sum();

        Double tasaVenta = totalUsdt == 0 ? 0 : totalP2P / totalUsdt;

        BalanceGeneral balance = balanceRepo.findByDate(fecha).orElse(new BalanceGeneral());
        balance.setDate(fecha);
        balance.setSaldo(saldoTotal);
        balance.setTasaCompra(tasaCompra);
        balance.setTasaVenta(tasaVenta);
        balance.setTotalP2P(totalP2P);
        balance.setComisionesP2P(comisionesP2P);
        balance.setTotalGeneralSales(totalGeneralSales);

        balanceRepo.save(balance);
    }

    @Override
    public List<BalanceGeneral> listarTodos() {
        return balanceRepo.findAll();
    }

    @Override
    public BalanceGeneral obtenerPorFecha(LocalDate fecha) {
        return balanceRepo.findByDate(fecha).orElse(null);
    }
}



