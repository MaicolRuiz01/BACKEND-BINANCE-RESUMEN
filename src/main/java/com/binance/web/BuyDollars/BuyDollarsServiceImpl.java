package com.binance.web.BuyDollars;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.Supplier.SupplierService;
@Service
public class BuyDollarsServiceImpl implements BuyDollarsService{
	
	@Autowired
    private BuyDollarsRepository buyDollarsRepository;
    
    @Autowired
    private SupplierRepository supplierRepository;
    
    @Autowired
    private AccountBinanceRepository accountBinanceRepository;

    @Override
    @Transactional  // asegura que las operaciones se hagan en una sola transacción
    public BuyDollars createBuyDollars(BuyDollarsDto dto) {
        // 1. Obtener el supplier con ID 1 desde la base de datos
        Supplier supplier = supplierRepository.findById(1)
                .orElseThrow(() -> new RuntimeException("Supplier with ID 1 not found"));
        
     // Obtener la cuenta de Binance correspondiente
        AccountBinance accountBinance = accountBinanceRepository.findById(dto.getAccountBinanceId())
                .orElseThrow(() -> new RuntimeException("Account not found"));
        
        // 2. Mapear los campos del DTO a la entidad BuyDollars
        BuyDollars buy = new BuyDollars();
        buy.setDollars(dto.getDollars());
        buy.setTasa(dto.getTasa());
        buy.setNameAccount(dto.getNameAccount());
        buy.setDate(dto.getDate());
        buy.setSupplier(supplier);
        buy.setAccountBinance(accountBinance);
        buy.setPesos(dto.getPesos());
        buy.setIdDeposit(dto.getIdDeposit());
        
        // 3. Actualizar el balance del supplier sumando el monto de la compra
        double montoSumar = dto.getDollars() * dto.getTasa();
        supplier.setBalance(supplier.getBalance() + montoSumar);
        
        // 4. Guardar los cambios en la base de datos
        supplierRepository.save(supplier);      // actualiza el supplier con el nuevo balance
        BuyDollars saved = buyDollarsRepository.save(buy);  // guarda la nueva compra de dólares
        
        return saved;
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
}
