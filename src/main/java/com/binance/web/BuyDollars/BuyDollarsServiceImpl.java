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
import com.binance.web.Entity.AccountCryptoBalance;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.Spot.SpotController;
import com.binance.web.averageRate.AverageRateService;
import com.binance.web.balance.PurchaseRate.PurchaseRateService;
import com.binance.web.model.TransaccionesDTO;

@Service
public class BuyDollarsServiceImpl implements BuyDollarsService {

	@Autowired
	private BuyDollarsRepository buyDollarsRepository;

	@Autowired
	private SupplierRepository supplierRepository;

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
	

	/*
	 * @Override
	 * 
	 * @Transactional public BuyDollars createBuyDollars(BuyDollarsDto dto) {
	 * Supplier supplier = supplierRepository.findById(dto.getSupplierId())
	 * .orElseThrow(() -> new RuntimeException("Supplier with ID " +
	 * dto.getSupplierId() + "not found"));
	 * 
	 * AccountBinance accountBinance =
	 * accountBinanceRepository.findById(dto.getAccountBinanceId()) .orElseThrow(()
	 * -> new RuntimeException("Account not found"));
	 * 
	 * BuyDollars buy = new BuyDollars(); buy.setDollars(dto.getDollars());
	 * buy.setTasa(dto.getTasa()); buy.setNameAccount(dto.getNameAccount());
	 * buy.setDate(dto.getDate()); buy.setSupplier(supplier);
	 * buy.setAccountBinance(accountBinance); buy.setPesos(dto.getPesos());
	 * buy.setIdDeposit(dto.getIdDeposit());
	 * 
	 * // Valores protegidos contra null Double dollars = dto.getDollars() != null ?
	 * dto.getDollars() : 0.0; Double tasa = dto.getTasa() != null ? dto.getTasa() :
	 * 0.0; Double supplierBalance = supplier.getBalance() != null ?
	 * supplier.getBalance() : 0.0; Double accountBalance =
	 * accountBinance.getBalance() != null ? accountBinance.getBalance() : 0.0;
	 * 
	 * double montoSumar = dollars * tasa; supplier.setBalance(supplierBalance +
	 * montoSumar); accountBinance.setBalance(accountBalance + dollars);
	 * 
	 * 
	 * AverageRate ultimaTasa = averageRateService.getUltimaTasaPromedio(); Double
	 * saldoTotalInternoAnteriorUSDT =
	 * accountBinanceService.getTotalBalanceInterno().doubleValue() - dollars;
	 * Double pesosAnteriores = saldoTotalInternoAnteriorUSDT *
	 * ultimaTasa.getAverageRate();
	 * 
	 * Double pesosTotal = pesosAnteriores + montoSumar; Double usdtTotal = dollars
	 * + saldoTotalInternoAnteriorUSDT; Double nuevaTasaPromedio = pesosTotal /
	 * usdtTotal; Double nuevoSaldo = saldoTotalInternoAnteriorUSDT + dollars;
	 * 
	 * //averageRateService.guardarNuevaTasa(nuevaTasaPromedio, nuevoSaldo);
	 * 
	 * accountBinanceRepository.save(accountBinance);
	 * supplierRepository.save(supplier); //desactivo esto porque da error
	 * purchaseRateService.addPurchaseRate(buy);
	 * 
	 * return buyDollarsRepository.save(buy); }
	 */

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

	        // Valores antiguos
	        Double oldAmount = existing.getAmount();
	        Double oldPesos = existing.getPesos();
	        Supplier oldSupplier = existing.getSupplier();
	        AccountBinance oldAccount = existing.getAccountBinance();
	        String oldCryptoSymbol = existing.getCryptoSymbol();
	        
	        // Valores nuevos
	        Double newAmount = dto.getAmount();
	        Double newTasa = dto.getTasa();
	        Double newPesos = newAmount * newTasa;
	        Integer newSupplierId = dto.getSupplierId();
	        Integer newAccountId = dto.getAccountBinanceId();
	        String newCryptoSymbol = dto.getCryptoSymbol();

	        // 1. Revertir saldos anteriores
	        if (oldSupplier != null) {
	            oldSupplier.setBalance(oldSupplier.getBalance() - oldPesos);
	            supplierRepository.save(oldSupplier);
	        }
	        if (oldAccount != null) {
	            accountBinanceService.subtractCryptoBalance(oldAccount.getId(), oldCryptoSymbol, oldAmount);
	        }

	        // 2. Actualizar la entidad BuyDollars
	        existing.setAmount(newAmount);
	        existing.setTasa(newTasa);
	        existing.setPesos(newPesos);
	        existing.setCryptoSymbol(newCryptoSymbol);
	        
	        // Asignar proveedor
	        Supplier newSupplier = supplierRepository.findById(newSupplierId)
	            .orElseThrow(() -> new RuntimeException("Nuevo proveedor no encontrado"));
	        existing.setSupplier(newSupplier);

	        // Asignar cuenta de Binance
	        AccountBinance newAccount = accountBinanceRepository.findById(newAccountId)
	            .orElseThrow(() -> new RuntimeException("Nueva cuenta de Binance no encontrada"));
	        existing.setAccountBinance(newAccount);
	        
	        // 3. Aplicar los nuevos saldos
	        Double currentNewSupplierBalance = newSupplier.getBalance() != null ? newSupplier.getBalance() : 0.0;
	        newSupplier.setBalance(currentNewSupplierBalance + newPesos);
	        supplierRepository.save(newSupplier);
	        
	        accountBinanceService.updateOrCreateCryptoBalance(newAccount.getId(), newCryptoSymbol, newAmount);
	        
	        // Se guarda la compra con los nuevos datos
	        return buyDollarsRepository.save(existing);
	    }

	// BuyDollarsServiceImpl.java

	@Override
    public void registrarComprasAutomaticamente() {
        try {
            // ✅ Modificación 1: Los controladores devuelven una lista de DTOs genéricos
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
                if (dto.getIdDeposit() == null || existentes.contains(dto.getIdDeposit())) {
                    continue;
                }

                AccountBinance account = accountBinanceRepository.findByName(dto.getNameAccount());
                if (account == null) {
                    continue;
                }

                // ✅ Modificación 2: Lógica genérica para actualizar/crear balances de criptos
                // Se usa el nuevo campo 'amount' y 'cryptoSymbol' del DTO
                accountBinanceService.updateOrCreateCryptoBalance(account.getId(), dto.getCryptoSymbol(), dto.getAmount());
                
                BuyDollars nueva = new BuyDollars();
                nueva.setIdDeposit(dto.getIdDeposit());
                nueva.setNameAccount(dto.getNameAccount());
                nueva.setDate(dto.getDate());
                nueva.setAmount(dto.getAmount());
                nueva.setCryptoSymbol(dto.getCryptoSymbol());
                nueva.setTasa(0.0);
                nueva.setPesos(0.0);
                nueva.setAsignada(false);
                nueva.setAccountBinance(account);
                
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
            // ✅ Uso del nuevo campo 'amount' en lugar de 'dollars'
            dto.setAmount(buy.getAmount());
            // ✅ Uso del nuevo campo 'cryptoSymbol'
            dto.setCryptoSymbol(buy.getCryptoSymbol());
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
        // Obtener la compra no asignada existente
        BuyDollars existing = buyDollarsRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Compra no encontrada"));
        
        if (Boolean.TRUE.equals(existing.getAsignada())) {
            throw new RuntimeException("Compra ya fue asignada");
        }

        // Insertar datos necesarios desde DTO:
        existing.setTasa(dto.getTasa());
        // ✅ Uso del nuevo campo 'amount' en lugar de 'dollars'
        existing.setPesos(existing.getAmount() * dto.getTasa());
        existing.setCryptoSymbol(dto.getCryptoSymbol());

        // Asignar proveedor
        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
            .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
        existing.setSupplier(supplier);

        // Actualizar balances del proveedor:
        Double montoPesos = existing.getAmount() * dto.getTasa();
        Double currentSupplierBalance = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
        supplier.setBalance(currentSupplierBalance + montoPesos);
        
        // Lógica de cálculo de la nueva tasa promedio (se basa en la nueva lógica)
        // ✅ Uso del servicio para obtener el balance anterior de la cripto específica
        Double saldoAnterior = accountBinanceService.getCryptoBalance(existing.getNameAccount(), existing.getCryptoSymbol());
        Double tasaPromedio = averageRateService.getUltimaTasaPromedio().getAverageRate();
        Double pesosAnterior = saldoAnterior * tasaPromedio;
        // ✅ Se utiliza el nuevo campo 'amount'
        Double totalUsdt = existing.getAmount() + saldoAnterior;
        Double nuevaTasaPromedio = ( pesosAnterior + montoPesos ) / totalUsdt;
        
        // Guardar la nueva tasa promedio
        averageRateService.guardarNuevaTasa(nuevaTasaPromedio, accountBinanceService.getTotalBalanceInterno().doubleValue(), existing.getDate());
        
        supplierRepository.save(supplier);

        // Marcar como asignada
        existing.setAsignada(true);

        return buyDollarsRepository.save(existing);
    }

	@Override
	public BuyDollars createBuyDollars(BuyDollarsDto buyDollarsDto) {
		// TODO Auto-generated method stub
		return null;
	}
}
