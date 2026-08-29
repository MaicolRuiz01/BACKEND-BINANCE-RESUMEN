package com.binance.web.serviceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.BalanceGeneral;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.CryptoAverageRate;
import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.Supplier;
import com.binance.web.Entity.AccountVes;
import com.binance.web.Entity.VesAverageRate;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.AccountVesRepository;
import com.binance.web.Repository.AverageRateRepository;
import com.binance.web.Repository.BalanceGeneralRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.BuyP2PRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.SaleP2PRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.Repository.VesAverageRateRepository;
import com.binance.web.model.CryptoResumenDiaDto;
import com.binance.web.service.AccountBinanceService;
import com.binance.web.service.BalanceGeneralService;
import com.binance.web.service.BuyDollarsService;
import com.binance.web.service.CryptoAverageRateService;
import com.binance.web.service.SaleP2PService;
import com.binance.web.service.SellDollarsService;
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
        private com.binance.web.Repository.MovimientoRepository movimientoRepository;
        @Autowired
        private CryptoAverageRateService cryptoAverageRateService;
        @Autowired
        private BuyDollarsService buyDollarsService;
        @Autowired
        private AccountVesRepository accountVesRepository;
        @Autowired
        private VesAverageRateRepository vesAverageRateRepository;
        @Autowired
        private BuyP2PRepository buyP2PRepository;
        @Autowired
        private SaleP2PRepository saleP2PRepository;
        @Autowired
        private SellDollarsRepository sellDollarsRepository;
        @Autowired
        private BuyDollarsRepository buyDollarsRepository;


        @Override
        @Transactional
        public void calcularOBalancear(LocalDate fecha) {

            BalanceGeneral balance = balanceRepo.findByDate(fecha)
                .orElseGet(() -> { var b = new BalanceGeneral(); b.setDate(fecha); return b; });
            
            double totalComprasNoAsignadasUsdt = calcularTotalComprasNoAsignadasUsdt(fecha);
            double totalVentasNoAsignadasUsdt  = calcularTotalVentasNoAsignadasUsdt(fecha);
            double totalComprasP2PNoAsignadasUsdt = calcularTotalComprasP2PNoAsignadasUsdt(fecha);
            double totalVentasP2PNoAsignadasUsdt  = calcularTotalVentasP2PNoAsignadasUsdt(fecha);


            // 1) tasa promedio del día (la misma que usarás para todo)
            Double tasaPromedioDelDia = averageRateRepository.findTopByOrderByFechaDesc()
                .map(AverageRate::getAverageRate)
                .orElseGet(() -> averageRateRepository.findTopByOrderByIdDesc()
                    .map(AverageRate::getAverageRate)
                    .orElse(0.0));

            // 2) total de TODAS las cuentas en USDT → a pesos.
            //
            // Se toma el saldo REAL leído de Binance/wallets, no el interno de la base. El interno
            // solo reflejaba lo que el sistema había alcanzado a registrar, así que cualquier
            // operación que no se importara bien lo dejaba descuadrado y el patrimonio salía mal
            // sin dar ningún error.
            //
            // Se usa la versión SOLO USDT a propósito: si se contaran todas las monedas valoradas
            // a precio de mercado (SOL, TRX, LINEA…), el patrimonio del día cambiaría solo porque
            // se movió el mercado, sin que se hubiera hecho ninguna operación.
            Double totalBinanceUSDT = accountBinanceService.getTotalExternalUsdt();
            if (totalBinanceUSDT == null) totalBinanceUSDT = 0.0;
            Double saldoCuentasBinance = totalBinanceUSDT * tasaPromedioDelDia;

            // 3) demás componentes del balance
            List<SellDollars> ventasGenerales = sellDollarsService.obtenerVentasPorFecha(fecha);

            // Cuentas COP para el balance: DEBE dar EXACTAMENTE lo mismo que el "TOTAL COP DISPONIBLE"
            // (endpoint /cuenta-cop/total-disponible). Fórmula única para que nunca se descuadren:
            //   Σ saldos de TODAS las cuentas COP
            //   − 4x1000 diferido pendiente (el de Bancolombia que se cobraría al día siguiente)
            //   y a ese neto se le saca el 4x1000 de sacarlo (× 0.996) sobre TODAS las cuentas.
            // NOTA: si cambias esta fórmula, cambia también AccountCopController.getTotalCopDisponible().
            java.util.List<AccountCop> todasCop = accountCopRepo.findAll();
            double sumaSaldosCop = todasCop.stream()
                .mapToDouble(a -> a.getBalance() != null ? a.getBalance() : 0.0).sum();
            double pendiente4x1000Bcol = movimientoRepository.sumComisionPendienteBancolombia();
            double netoCop = sumaSaldosCop - pendiente4x1000Bcol;
            Double saldoCop = netoCop * 0.996; // 4x1000 para sacarlo

            Double saldoProveedores = accountProveedorRepo.findAll().stream()
                .mapToDouble(Supplier::getBalance).sum();

            Double saldoCajas = efectivoRepository.findAll().stream()
                .mapToDouble(Efectivo::getSaldo).sum();

            Double clientesSaldo = clienteRepository.findAll().stream()
                .mapToDouble(Cliente::getSaldo).sum();

            Double totalP2P = saleP2PService.obtenerVentasPorFecha(fecha).stream()
                .mapToDouble(SaleP2P::getPesosCop).sum();

            Double totalUsdt = saleP2PService.obtenerVentasPorFecha(fecha).stream()
                .mapToDouble(SaleP2P::getDollarsUs).sum();

            Double totalUSDTVentasGenerales = sellDollarsService.obtenerVentasPorFecha(fecha).stream()
                .mapToDouble(SellDollars::getDollars).sum();

            Double pesosVentasGenerales = sellDollarsService.obtenerVentasPorFecha(fecha).stream()
                .mapToDouble(SellDollars::getPesos).sum();
            
            Double totalVesCuentas = accountVesRepository.findAll().stream()
                    .mapToDouble(acc -> acc.getBalance() == null ? 0.0 : acc.getBalance())
                    .sum();
            
            
            double tasaPromedioVes = vesAverageRateRepository.findByDia(fecha).stream()
                    .sorted((a, b) -> b.getFechaCalculo().compareTo(a.getFechaCalculo())) // última del día
                    .map(VesAverageRate::getTasaPromedioDia)
                    .findFirst()
                    .orElseGet(() -> 
                        vesAverageRateRepository.findTopByOrderByFechaCalculoDesc()
                            .map(VesAverageRate::getTasaPromedioDia)
                            .orElse(0.0)
                    );
            
            Double pesosTotalCuentasVES = totalVesCuentas * tasaPromedioVes;
            
            
            
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
            double netoNoAsignadasUsdt =
            	    (
            	      (totalVentasNoAsignadasUsdt + totalVentasP2PNoAsignadasUsdt)
            	      -
            	      (totalComprasNoAsignadasUsdt + totalComprasP2PNoAsignadasUsdt)
            	    ) * tasaPromedioDelDia;

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
            Double saldoTotal = saldoCuentasBinance + saldoCajas + saldoCop + pesosTotalCuentasVES - saldoProveedores - clientesSaldo + netoNoAsignadasUsdt;
            
            // Desglose de criptos: se deja VACÍO. El negocio maneja solo USDT, así que ya no se
            // lleva una tasa promedio por cada moneda (TRX, SOL, ZBT…) y no hay nada que desglosar.
            // Se conserva el campo y el JSON vacío para no romper los balances ya guardados ni
            // la vista que lo lee.
            List<CryptoResumenDiaDto> resumenCriptos = List.of();

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
            balance.setNetoNoAsignadasUsdt(netoNoAsignadasUsdt);
            balance.setSaldosVES(pesosTotalCuentasVES);
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

        /**
         * Neto (en COP) de TODO lo que está pendiente por asignar HOY: compras/ventas dólares
         * + compras/ventas P2P. Se calcula AISLADO del balance general completo (que hace muchas
         * más consultas y puede fallar por otras causas), así la card "Asignar" siempre refleja
         * exactamente lo que se ve en las vistas de asignación. NULL-safe (asignado=null cuenta).
         */
        @Override
        public double calcularNetoNoAsignadoHoy() {
                LocalDate fecha = LocalDate.now(ZoneId.of("America/Bogota"));
                double comprasDolares = calcularTotalComprasNoAsignadasUsdt(fecha);
                double ventasDolares  = calcularTotalVentasNoAsignadasUsdt(fecha);
                double comprasP2P     = calcularTotalComprasP2PNoAsignadasUsdt(fecha);
                double ventasP2P      = calcularTotalVentasP2PNoAsignadasUsdt(fecha);

                double tasa = averageRateRepository.findTopByOrderByFechaDesc()
                        .map(AverageRate::getAverageRate)
                        .orElseGet(() -> averageRateRepository.findTopByOrderByIdDesc()
                                .map(AverageRate::getAverageRate)
                                .orElse(0.0));

                return ((ventasDolares + ventasP2P) - (comprasDolares + comprasP2P)) * tasa;
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
        
        private double safe(Double d) {
            return d == null ? 0.0 : d;
        }

        /**
         * Convierte un monto de una cripto a USDT usando la tasa promedio del día.
         * Si la cripto es USDT o null, simplemente retorna el monto.
         */
        private double convertirCriptoAUsdt(String cryptoSymbol, Double amount, LocalDate fecha) {
            if (amount == null || amount == 0) return 0.0;
            if (cryptoSymbol == null || "USDT".equalsIgnoreCase(cryptoSymbol)) {
                return amount;
            }

            // El negocio maneja SOLO USDT. Ya no se lleva tasa promedio por cada cripto, así que
            // una compra en otra moneda no se puede valorar y NO entra al balance.
            //
            // Se deja el aviso en el log a propósito: antes esto devolvía 0 en silencio y esa
            // plata desaparecía del balance sin que nadie se enterara. Si aparece esta línea,
            // es que entró algo que no es USDT y hay que revisarlo a mano.
            System.out.println("⚠️ [BalanceGeneral] Compra en " + cryptoSymbol
                    + " ignorada: el sistema solo maneja USDT. Monto sin valorar: " + amount);
            return 0.0;
        }

        /**
         * Total de COMPRAS no asignadas del día en USDT.
         * Usa amount + cryptoSymbol de BuyDollars.
         */
        private double calcularTotalComprasNoAsignadasUsdt(LocalDate fecha) {
            LocalDateTime end = endOfDay(fecha);

            // Ideal: crea en repo un método findByAsignadaFalseAndDateLessThan(end)
            List<BuyDollars> comprasNoAsignadas = buyDollarsRepository.findNoAsignadasBeforeNullSafe(end);

            return comprasNoAsignadas.stream()
                .mapToDouble(c -> convertirCriptoAUsdt(
                    c.getCryptoSymbol(),
                    c.getAmount(),
                    c.getDate() != null ? c.getDate().toLocalDate() : fecha
                ))
                .sum();
        }


        /**
         * Total de VENTAS no asignadas del día en USDT.
         * Asumo que 'dollars' es la cantidad en la cripto indicada.
         * Si en tu modelo 'dollars' YA está en USDT, solo cambia la lógica.
         */
        private double calcularTotalVentasNoAsignadasUsdt(LocalDate fecha) {
            LocalDateTime end = endOfDay(fecha);

            List<SellDollars> ventasNoAsignadas = sellDollarsRepository.findNoAsignadasBeforeNullSafe(end);

            return ventasNoAsignadas.stream()
                .mapToDouble(v -> convertirCriptoAUsdt(
                    v.getCryptoSymbol(),
                    v.getDollars(),
                    v.getDate() != null ? v.getDate().toLocalDate() : fecha
                ))
                .sum();
        }


        
        private double calcularTotalComprasP2PNoAsignadasUsdt(LocalDate fecha) {
            LocalDateTime end = endOfDay(fecha);

            // NULL-safe: incluye compras con asignado=null.
            return buyP2PRepository.findNoAsignadasBeforeNullSafe(end).stream()
                .mapToDouble(b -> safe(b.getDollarsUs()))
                .sum();
        }


        private double calcularTotalVentasP2PNoAsignadasUsdt(LocalDate fecha) {
            LocalDateTime end = endOfDay(fecha);

            // NULL-safe: incluye ventas con asignado=null (el derived query las ignoraba y el
            // balance daba 0 aunque en "Ventas Pendientes" sí aparecieran).
            return saleP2PRepository.findNoAsignadasBeforeNullSafe(end).stream()
                .mapToDouble(s -> safe(s.getDollarsUs()))
                .sum();
        }

        
        private LocalDateTime endOfDay(LocalDate fecha) {
            return fecha.plusDays(1).atStartOfDay(); // exclusivo
        }

}
