package com.binance.web.balanceGeneral;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.BalanceGeneral;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.CryptoAverageRate;
import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.AverageRateRepository;
import com.binance.web.Repository.BalanceGeneralRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.EfectivoRepository;

import com.binance.web.Repository.SupplierRepository;
import com.binance.web.SaleP2P.SaleP2PService;
import com.binance.web.SellDollars.SellDollarsService;
import com.binance.web.cryptoAverageRate.CryptoAverageRateService;
import com.binance.web.model.CryptoResumenDiaDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;

@Service
public class BalanceGeneralServiceImplement implements BalanceGeneralService {

        @Autowired
        private BalanceGeneralRepository balanceRepo;
        @Autowired
        private AccountBinanceRepository accountBinanceRepo;
        @Autowired
        private AccountCopRepository accountCopRepo;
        @Autowired
        private SupplierRepository accountProveedorRepo;

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
        @Autowired
        private ClienteRepository clienteRepository;
        @Autowired
        private SupplierRepository supplierRepository;
        @Autowired
        private AccountCopRepository accountCopRepository;
        @Autowired
        private CryptoAverageRateService cryptoAverageRateService;

        @Override
        @Transactional
        public void calcularOBalancear(LocalDate fecha) {

            BalanceGeneral balance = balanceRepo.findByDate(fecha)
                .orElseGet(() -> { var b = new BalanceGeneral(); b.setDate(fecha); return b; });

            // 1) tasa promedio del día (la misma que usarás para todo)
            Double tasaPromedioDelDia = averageRateRepository.findTopByOrderByFechaDesc()
                .map(AverageRate::getAverageRate)
                .orElseGet(() -> averageRateRepository.findTopByOrderByIdDesc()
                    .map(AverageRate::getAverageRate)
                    .orElse(0.0));

            // 2) total de TODAS las cuentas en USDT (interno) → a pesos
            Double totalBinanceUSDT = accountBinanceService.getTotalBalanceInterno().doubleValue();
            Double saldoCuentasBinance = totalBinanceUSDT * tasaPromedioDelDia;

            // 3) demás componentes del balance
            List<SellDollars> ventasGenerales = sellDollarsService.obtenerVentasPorFecha(fecha);

            Double saldoCop = accountCopRepo.findAll().stream()
                .mapToDouble(AccountCop::getBalance).sum();

            Double saldoProveedores = accountProveedorRepo.findAll().stream()
                .mapToDouble(Supplier::getBalance).sum();

            Double saldoCajas = efectivoRepository.findAll().stream()
                .mapToDouble(Efectivo::getSaldo).sum();

            Double clientesSaldo = clienteRepository.findAll().stream()
                .mapToDouble(Cliente::getSaldo).sum();

            // 4) usa el NUEVO campo en el total
            Double saldoTotal = saldoCuentasBinance + saldoCajas + saldoCop - saldoProveedores + clientesSaldo;

            Double totalP2P = saleP2PService.obtenerVentasPorFecha(fecha).stream()
                .mapToDouble(SaleP2P::getPesosCop).sum();

            Double totalUsdt = saleP2PService.obtenerVentasPorFecha(fecha).stream()
                .mapToDouble(SaleP2P::getDollarsUs).sum();

            Double totalUSDTVentasGenerales = sellDollarsService.obtenerVentasPorFecha(fecha).stream()
                .mapToDouble(SellDollars::getDollars).sum();

            Double pesosVentasGenerales = sellDollarsService.obtenerVentasPorFecha(fecha).stream()
                .mapToDouble(SellDollars::getPesos).sum();
            
            double deudaProveedores = supplierRepository.findAll().stream()
            	    .mapToDouble(s -> s.getBalance() == null ? 0.0 : s.getBalance())
            	    .sum();


            Double tasaVenta = totalUsdt == 0 ? 0 : totalP2P / totalUsdt;

            Double comisionTrust = ventasGenerales.stream()
                .filter(v -> v.getComision() != null)
                .mapToDouble(SellDollars::getComision)
                .sum() * tasaPromedioDelDia;

            Double comisionesP2P = saleP2PService.obtenerComisionesPorFecha(fecha) * tasaPromedioDelDia;
            Double cuatroPorMil   = totalP2P * 0.004;

            Double utilidadP2P = (totalUsdt * tasaVenta)
                - (totalUsdt * tasaPromedioDelDia)
                - comisionesP2P - cuatroPorMil;

            Double tasaVentaGenerales = 0.0;
            if (pesosVentasGenerales != null && pesosVentasGenerales != 0) {
                tasaVentaGenerales = totalUSDTVentasGenerales / pesosVentasGenerales;
            }
            if (Double.isInfinite(tasaVentaGenerales) || Double.isNaN(tasaVentaGenerales)) {
                tasaVentaGenerales = 0.0;
            }

            Double utilidadVentasGenerales = (totalUSDTVentasGenerales * tasaVentaGenerales)
                - (totalUSDTVentasGenerales * tasaPromedioDelDia);
            
            List<CryptoAverageRate> ratesHoy = cryptoAverageRateService.listarPorDia(fecha);

            List<CryptoResumenDiaDto> resumenCriptos = ratesHoy.stream()
                .filter(r -> r.getSaldoFinalCripto() != null && r.getSaldoFinalCripto() > 0.000001)
                .map(r -> {
                    Double saldo = r.getSaldoFinalCripto();
                    Double tasa  = r.getTasaPromedioDia();
                    Double usdt  = (saldo != null && tasa != null) ? saldo * tasa : 0.0;
                    return new CryptoResumenDiaDto(
                            r.getCripto(),
                            saldo,
                            tasa,
                            usdt
                    );
                })
                .toList();

            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(resumenCriptos);
                balance.setDetalleCriptosJson(json);
            } catch (Exception ex) {
                // si falla el JSON, al menos no rompemos el cálculo del balance
                balance.setDetalleCriptosJson("[]");
            }

            // persistir
            balance.setDate(fecha);
            balance.setSaldo(saldoTotal);
            balance.setSaldoCuentasBinance(saldoCuentasBinance); // 👈 nuevo campo llenado en COP
            balance.setTasaPromedioDelDia(tasaPromedioDelDia);
            balance.setTasaVentaDelDia(tasaVenta);
            balance.setTotalP2PdelDia(totalUsdt);
            balance.setComisionesP2PdelDia(comisionesP2P);
            balance.setCuatroPorMilDeVentas(cuatroPorMil);
            balance.setUtilidadP2P(utilidadP2P);
            balance.setTotalVentasGeneralesDelDia(totalUSDTVentasGenerales);
            balance.setUtilidadVentasGenerales(utilidadVentasGenerales);
            balance.setEfectivoDelDia(saldoCajas);
            balance.setSaldoClientes(clientesSaldo);
            balance.setComisionTrust(comisionTrust);
            balance.setProveedores(deudaProveedores);
            balance.setCuentasCop(saldoCop);

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
                LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));
                calcularOBalancear(today);

                return balanceRepo.findByDate(today).orElse(null);
        }

        @Override
        public Double obtenerTotalCajas() {
                return efectivoRepository.findAll().stream()
                                .mapToDouble(e -> e.getSaldo())
                                .sum();
        }

        @Override
        public Double obtenerTotalClientes() {
                return clienteRepository.findAll().stream()
                                .mapToDouble(c -> c.getSaldo())
                                .sum();
        }
}
