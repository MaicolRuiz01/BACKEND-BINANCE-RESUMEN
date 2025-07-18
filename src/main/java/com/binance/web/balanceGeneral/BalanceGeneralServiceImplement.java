package com.binance.web.balanceGeneral;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.AverageRateRepository;
import com.binance.web.Repository.BalanceGeneralRepository;
import com.binance.web.Repository.EfectivoRepository;
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
    @Autowired
    private AverageRateRepository averageRateRepository;
    @Autowired
    private AccountBinanceService accountBinanceService;
    @Autowired
    private EfectivoRepository efectivoRepository;

    @Override
    @Transactional
    public void calcularOBalancear(LocalDate fecha) {
    	
    	//verifica si es el balance del dia o si es uno nuevo
    	BalanceGeneral balance = balanceRepo.findByDate(fecha)
    	        .orElseGet(() -> {
    	            BalanceGeneral nuevo = new BalanceGeneral();
    	            nuevo.setDate(fecha);
    	            return nuevo;
    	        });
        
        
        Double saldoBinanceTotalEnPesos = accountBinanceService.getTotalBalance().doubleValue();
        //obtienen todos los saldos de los nequis y los suma
        Double saldoCop = accountCopRepo.findAll().stream()
            .mapToDouble(a -> a.getBalance())
            .sum();
        //obtiene todos los saldos de los proveedores y los suma
        Double saldoProveedores = accountProveedorRepo.findAll().stream()
            .mapToDouble(a -> a.getBalance())
            .sum();
        
        Double saldoCajas = efectivoRepository.findAll().stream()
        		.mapToDouble(a -> a.getSaldo())
        		.sum();

        Double saldoTotal = saldoBinanceTotalEnPesos + saldoCajas + saldoCop - saldoProveedores;

        Double totalP2P = saleP2PService.obtenerVentasPorFecha(fecha).stream()
        	    .mapToDouble(SaleP2P::getPesosCop)
        	    .sum();

        

      //  Double totalGeneralSales = sellDollarsService.obtenerVentasPorFecha(fecha).stream()
          //  .mapToDouble(v -> v.getPesos())
           // .sum();

        Double totalUsdt = saleP2PService.obtenerVentasPorFecha(fecha).stream()
            .mapToDouble(v -> v.getDollarsUs())
            .sum();
        
        Double totalUSDTVentasGenrales= sellDollarsService.obtenerVentasPorFecha(fecha).stream()
        		.mapToDouble(v -> v.getDollars())
        		.sum();
        
        Double pesosVentasGenerales = sellDollarsService.obtenerVentasPorFecha(fecha).stream()
        		.mapToDouble(v -> v.getPesos())
        		.sum();
        
        Double tasaVenta = totalUsdt == 0 ? 0 : totalP2P / totalUsdt;
        
        
        
     // NUEVO: Sacar tasa promedio del día
        Double tasaPromedioDelDia = averageRateRepository.findTopByOrderByFechaDesc()
            .map(AverageRate::getAverageRate)
            .orElseGet(() -> averageRateRepository.findTopByOrderByIdDesc()
            .map(AverageRate::getAverageRate)
            .orElse(0.0));
        
        Double comisionesP2P = saleP2PService.obtenerComisionesPorFecha(fecha) * tasaPromedioDelDia;
        Double cuatroPorMil = totalP2P * 0.004;
        
        Double utilidadP2P = (totalUsdt * tasaVenta) - (totalUsdt *  tasaPromedioDelDia) - comisionesP2P - cuatroPorMil ;
        Double tasaVentaGenerales = totalUSDTVentasGenrales == 0 ? 0 : totalUSDTVentasGenrales / pesosVentasGenerales; 
        Double utilidadVentasGenerales = (totalUSDTVentasGenrales * tasaVentaGenerales) - (totalUSDTVentasGenrales * tasaPromedioDelDia);
        
        
        		
        balance.setDate(fecha);
        balance.setSaldo(saldoTotal);
        balance.setTasaPromedioDelDia(tasaPromedioDelDia);
        balance.setTasaVentaDelDia(tasaVenta);
        balance.setTotalP2PdelDia(totalUsdt);
        balance.setComisionesP2PdelDia(comisionesP2P);
      //  balance.setTotalGeneralSales(totalGeneralSales);
        balance.setCuatroPorMilDeVentas(cuatroPorMil);
        balance.setUtilidadP2P(utilidadP2P);
        balance.setTotalVentasGeneralesDelDia(totalUSDTVentasGenrales);
        balance.setUtilidadVentasGenerales(utilidadVentasGenerales);
        
        System.out.println("Hoy es (según el backend): " + LocalDate.now());
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
    
    @Override
    public BalanceGeneral calcularHoyYRetornar() {
        LocalDate today = LocalDate.now();
        calcularOBalancear(today);

        return balanceRepo.findByDate(today).orElse(null);
    }

}



