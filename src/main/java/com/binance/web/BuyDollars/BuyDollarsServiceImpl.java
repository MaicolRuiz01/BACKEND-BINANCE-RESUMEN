package com.binance.web.BuyDollars;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.BinanceAPI.PaymentController;
import com.binance.web.BinanceAPI.SpotOrdersController;
import com.binance.web.BinanceAPI.TronScanController;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.averageRate.AverageRateService;
import com.binance.web.balance.PurchaseRate.PurchaseRateService;

@Service
public class BuyDollarsServiceImpl implements BuyDollarsService {

    @Autowired
    private BuyDollarsRepository buyDollarsRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AccountBinanceRepository accountBinanceRepository;
    
    @Autowired
    private AccountBinanceService accountBinanceService;

    @Autowired
    private PurchaseRateService purchaseRateService;
    
    @Autowired
    private AverageRateService averageRateService;

    @Autowired
    private PaymentController binancePayController;

    @Autowired
    private SpotOrdersController spotOrdersController;

    @Autowired
    private TronScanController tronScanController;

    @Override
    @Transactional
    public BuyDollars createBuyDollars(BuyDollarsDto dto) {
        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier with ID " + dto.getSupplierId() + " not found"));

        AccountBinance accountBinance = accountBinanceRepository.findById(dto.getAccountBinanceId())
                .orElseThrow(() -> new RuntimeException("Account not found"));
        
        BuyDollars buy = new BuyDollars();
        buy.setDollars(dto.getDollars());
        buy.setTasa(dto.getTasa());
        buy.setNameAccount(dto.getNameAccount());
        buy.setDate(dto.getDate());
        buy.setSupplier(supplier);
        buy.setAccountBinance(accountBinance);
        buy.setPesos(dto.getPesos());
        buy.setIdDeposit(dto.getIdDeposit());

        Double dollars = dto.getDollars() != null ? dto.getDollars() : 0.0;
        Double tasa = dto.getTasa() != null ? dto.getTasa() : 0.0;
        Double supplierBalance = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
        Double accountBalance = accountBinance.getBalance() != null ? accountBinance.getBalance() : 0.0;

        double montoSumar = dollars * tasa;
        supplier.setBalance(supplierBalance + montoSumar);
        accountBinance.setBalance(accountBalance + dollars);
        
        AverageRate ultimaTasa = averageRateService.getUltimaTasaPromedio();
        Double saldoTotalInternoAnteriorUSDT = accountBinanceService.getTotalBalanceInterno().doubleValue() - dollars;
        Double pesosAnteriores = saldoTotalInternoAnteriorUSDT * ultimaTasa.getAverageRate();
        
        Double pesosTotal = pesosAnteriores + montoSumar;
        Double usdtTotal = dollars + saldoTotalInternoAnteriorUSDT;
        Double nuevaTasaPromedio = pesosTotal / usdtTotal;
        Double nuevoSaldo = saldoTotalInternoAnteriorUSDT + dollars;
        
        //averageRateService.guardarNuevaTasa(nuevaTasaPromedio, nuevoSaldo);
        
        accountBinanceRepository.save(accountBinance);
        supplierRepository.save(supplier);
        purchaseRateService.addPurchaseRate(buy);

        return buyDollarsRepository.save(buy);
    }

    @Override
    public BuyDollars getLastBuyDollars() {
        BuyDollars lastBuy = buyDollarsRepository.findTopByOrderByDateDesc();
        if (lastBuy == null) {
            throw new RuntimeException("No hay registros de BuyDollars");
        }
        return lastBuy;
    }
    
    @Override
    public List<BuyDollars> getAllBuyDollars() {
        return buyDollarsRepository.findAll(Sort.by(Sort.Direction.DESC, "date"));
    }
    
    @Override
    @Transactional
    public BuyDollars updateBuyDollars(Integer id, BuyDollarsDto dto) {
        BuyDollars existing = buyDollarsRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Compra con ID " + id + " no encontrada"));

        // Validaciones mínimas
        if (dto.getSupplierId() != null && dto.getClienteId() != null) {
            throw new RuntimeException("No puede actualizar con proveedor y cliente a la vez");
        }
        if (dto.getSupplierId() == null && dto.getClienteId() == null) {
            throw new RuntimeException("Debe enviar supplierId o clienteId");
        }

        // Monto anterior
        Double oldDollars = existing.getDollars();
        Double oldTasa = existing.getTasa();
        double oldAmountPesos = oldDollars * oldTasa;

        Supplier oldSupplier = existing.getSupplier();
        Cliente oldCliente = existing.getCliente();
        AccountBinance oldAccount = existing.getAccountBinance();

        // Revertir balances anteriores
        if (oldSupplier != null) {
            oldSupplier.setBalance(oldSupplier.getBalance() - oldAmountPesos);
            supplierRepository.save(oldSupplier);
        }
        if (oldCliente != null) {
            oldCliente.setSaldo(oldCliente.getSaldo() + oldAmountPesos);
            clienteRepository.save(oldCliente);
        }
        if (oldAccount != null) {
            oldAccount.setBalance(oldAccount.getBalance() - oldDollars);
            accountBinanceRepository.save(oldAccount);
        }

        // Nuevos valores
        Double newDollars = dto.getDollars();
        Double newTasa = dto.getTasa();
        double newAmountPesos = newDollars * newTasa;

        existing.setDollars(newDollars);
        existing.setTasa(newTasa);
        existing.setPesos(dto.getPesos());
        existing.setDate(dto.getDate());
        existing.setNameAccount(dto.getNameAccount());
        existing.setIdDeposit(dto.getIdDeposit());

        // Nuevo supplier
        if (dto.getSupplierId() != null) {
            Supplier newSupplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
            existing.setSupplier(newSupplier);
            existing.setCliente(null); // se limpia cliente
            Double balance = newSupplier.getBalance() != null ? newSupplier.getBalance() : 0.0;
            newSupplier.setBalance(balance + newAmountPesos);
            supplierRepository.save(newSupplier);
        }

        // Nuevo cliente
        if (dto.getClienteId() != null) {
            Cliente newCliente = clienteRepository.findById(dto.getClienteId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
            existing.setCliente(newCliente);
            existing.setSupplier(null); // se limpia supplier
            Double saldo = newCliente.getSaldo() != null ? newCliente.getSaldo() : 0.0;
            newCliente.setSaldo(saldo - newAmountPesos);
            clienteRepository.save(newCliente);
        }

        // Nueva cuenta Binance
        if (!oldAccount.getId().equals(dto.getAccountBinanceId())) {
            AccountBinance newAccount = accountBinanceRepository.findById(dto.getAccountBinanceId())
                .orElseThrow(() -> new RuntimeException("Cuenta Binance no encontrada"));
            existing.setAccountBinance(newAccount);
            Double saldo = newAccount.getBalance() != null ? newAccount.getBalance() : 0.0;
            newAccount.setBalance(saldo + newDollars);
            accountBinanceRepository.save(newAccount);
        } else {
            oldAccount.setBalance(oldAccount.getBalance() + newDollars);
            accountBinanceRepository.save(oldAccount);
        }

        return buyDollarsRepository.save(existing);
    }

    @Override
    @Transactional
    public void registrarComprasAutomaticamente() {
        try {
            List<BuyDollarsDto> binancePay = binancePayController.getComprasNoRegistradas().getBody();
            List<BuyDollarsDto> spot = spotOrdersController.getComprasNoRegistradas(20).getBody();
            List<BuyDollarsDto> trust = tronScanController.getUSDTIncomingTransfers().getBody();

            Set<String> existentes = buyDollarsRepository.findAll().stream()
                .map(BuyDollars::getIdDeposit)
                .collect(Collectors.toSet());

            List<BuyDollarsDto> todas = new ArrayList<>();
            if (binancePay != null) todas.addAll(binancePay);
            if (spot != null) todas.addAll(spot);
            if (trust != null) todas.addAll(trust);

            todas.sort(Comparator.comparing(BuyDollarsDto::getDate));

            for (BuyDollarsDto dto : todas) {
                if (dto.getIdDeposit() == null || existentes.contains(dto.getIdDeposit())) continue;

                AccountBinance account = accountBinanceRepository.findByName(dto.getNameAccount());
                if (account == null) continue;

                Double actual = account.getBalance() != null ? account.getBalance() : 0.0;
                account.setBalance(actual + dto.getDollars());
                accountBinanceRepository.save(account);

                BuyDollars nueva = new BuyDollars();
                nueva.setIdDeposit(dto.getIdDeposit());
                nueva.setNameAccount(dto.getNameAccount());
                nueva.setDate(dto.getDate());
                nueva.setDollars(dto.getDollars());
                nueva.setTasa(0.0);
                nueva.setPesos(0.0);
                nueva.setAsignada(false);
                nueva.setAccountBinance(account);
                nueva.setSaldoAnterior(actual);
                
                buyDollarsRepository.save(nueva);
                existentes.add(dto.getIdDeposit());
            }

        } catch (Exception e) {
            throw new RuntimeException("Error al registrar compras automáticamente", e);
        }
    }
    
    @Override
    public List<BuyDollarsDto> getComprasNoAsignadasHoy() {
        ZoneId zoneId = ZoneId.of("America/Bogota");
        LocalDateTime start = LocalDate.now(zoneId).atStartOfDay();
        LocalDateTime end = LocalDate.now(zoneId).atTime(LocalTime.MAX);
        List<BuyDollars> compras = buyDollarsRepository.findNoAsignadasHoy(start, end);

        return compras.stream().map(buy -> {
            BuyDollarsDto dto = new BuyDollarsDto();
            dto.setId(buy.getId());
            dto.setDollars(buy.getDollars());
            dto.setTasa(buy.getTasa());
            dto.setNameAccount(buy.getNameAccount());
            dto.setDate(buy.getDate());
            dto.setIdDeposit(buy.getIdDeposit());
            dto.setPesos(buy.getPesos());
            dto.setAccountBinanceId(buy.getAccountBinance().getId());
            dto.setAsignada(buy.getAsignada());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BuyDollars asignarCompra(Integer id, BuyDollarsDto dto) {
        BuyDollars existing = buyDollarsRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Compra no encontrada"));

        if (Boolean.TRUE.equals(existing.getAsignada())) {
            throw new RuntimeException("Compra ya fue asignada");
        }

        if (dto.getSupplierId() != null && dto.getClienteId() != null) {
            throw new RuntimeException("No puede asignarse proveedor y cliente a la vez");
        }
        if (dto.getSupplierId() == null && dto.getClienteId() == null) {
            throw new RuntimeException("Debe enviar supplierId o clienteId");
        }

        existing.setTasa(dto.getTasa());
        existing.setPesos(existing.getDollars() * dto.getTasa());

        Double montoPesos = existing.getDollars() * dto.getTasa();

        if (dto.getSupplierId() != null) {
            Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
            existing.setSupplier(supplier);

            Double currentSupplierBalance = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
            supplier.setBalance(currentSupplierBalance + montoPesos);
            supplierRepository.save(supplier);
        }

        if (dto.getClienteId() != null) {
            Cliente cliente = clienteRepository.findById(dto.getClienteId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
            existing.setCliente(cliente);

            Double currentClienteSaldo = cliente.getSaldo() != null ? cliente.getSaldo() : 0.0;
            cliente.setSaldo(currentClienteSaldo - montoPesos);
            clienteRepository.save(cliente);
        }

        Double saldoAnterior = existing.getSaldoAnterior() != null ? existing.getSaldoAnterior() : 0.0;
        Double tasaPromedio = averageRateService.getUltimaTasaPromedio().getAverageRate();
        Double pesosAnterior = saldoAnterior * tasaPromedio;
        Double totalUsdt = existing.getDollars() + saldoAnterior;
        Double nuevaTasaPromedio = (pesosAnterior + montoPesos) / totalUsdt;

        averageRateService.guardarNuevaTasa(
            nuevaTasaPromedio,
            accountBinanceService.getTotalBalanceInterno().doubleValue(),
            existing.getDate()
        );

        existing.setAsignada(true);
        return buyDollarsRepository.save(existing);
    }
}
