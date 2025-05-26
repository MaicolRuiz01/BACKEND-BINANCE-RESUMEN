package com.binance.web.SellDollars;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.Entity.Supplier;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SellDollarsServiceImpl implements SellDollarsService{
	
	@Autowired
    private SellDollarsRepository sellDollarsRepository;
	
	@Autowired
    private AccountBinanceRepository accountBinanceRepository;
	@Autowired
	private SupplierRepository supplierRepository;

	@Override
    @Transactional
    public SellDollars createSellDollars(SellDollarsDto dto) {
        AccountBinance accountBinance = accountBinanceRepository.findById(dto.getAccountBinanceId())
                .orElseThrow(() -> new RuntimeException("AccountBinance not found"));

        Supplier supplier = supplierRepository.findById(1).orElseThrow(() -> new RuntimeException("Account not found"));

        SellDollars sale = new SellDollars();
        sale.setIdWithdrawals(dto.getIdWithdrawals());
        sale.setTasa(dto.getTasa());
        sale.setDollars(dto.getDollars());
        sale.setDate(dto.getDate());
        sale.setNameAccount(dto.getNameAccount());
        sale.setAccountBinance(accountBinance);
        sale.setPesos(dto.getPesos());

        // Convertir null a 0 para evitar NullPointerException en las operaciones
        Double accountBalance = accountBinance.getBalance() != null ? accountBinance.getBalance() : 0.0;
        Double dollars = dto.getDollars() != null ? dto.getDollars() : 0.0;
        Double supplierBalance = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
        Double pesos = dto.getPesos() != null ? dto.getPesos() : 0.0;

        accountBinance.setBalance(accountBalance - dollars);
        supplier.setBalance(supplierBalance - pesos);

        supplierRepository.save(supplier);
        accountBinanceRepository.save(accountBinance);

        return sellDollarsRepository.save(sale);
    }

}
