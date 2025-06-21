package com.binance.web.AccountBinance;

import java.util.List;
import org.springframework.stereotype.Service;

import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Repository.AccountBinanceRepository;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class AccountBinanceServiceImpl implements AccountBinanceService {

	 private final BinanceService binanceService; 
    private final AccountBinanceRepository accountBinanceRepository;

    public AccountBinanceServiceImpl(AccountBinanceRepository accountBinanceRepository, BinanceService binanceService) {
        this.accountBinanceRepository = accountBinanceRepository;
        this.binanceService = binanceService;
    }

    @Override
    public List<AccountBinance> findAllAccountBinance() {
        return accountBinanceRepository.findAll();
    }

    @Override
    public AccountBinance findByIdAccountBinance(Integer id) {
        return accountBinanceRepository.findById(id).orElse(null);
    }

    @Override
    public void saveAccountBinance(AccountBinance accountBinance) {
        accountBinanceRepository.save(accountBinance);
    }

    @Override
    public void updateAccountBinance(Integer id, AccountBinance updatedAccountBinance) {
        AccountBinance existing = accountBinanceRepository.findById(id).orElse(null);
        if (existing != null) {
            existing.setName(updatedAccountBinance.getName());
            existing.setReferenceAccount(updatedAccountBinance.getReferenceAccount());
            existing.setBalance(updatedAccountBinance.getBalance());
            existing.setCorreo(updatedAccountBinance.getCorreo());
            accountBinanceRepository.save(existing);
        }
    }

    @Override
    public void deleteAccountBinance(Integer id) {
        accountBinanceRepository.deleteById(id);
    }
    
    @Override
    public AccountBinance findByName(String name) {
        return accountBinanceRepository.findByName(name);
    }
    
    public void subtractBalance(String name, Double amount) {
        AccountBinance account = accountBinanceRepository.findByName(name);
        if (account != null && amount != null && account.getBalance() != null) {
            account.setBalance(account.getBalance() - amount);
            accountBinanceRepository.save(account);
        }
        System.out.println("🔴 Restando saldo: " + amount + " a cuenta: " + name);

    }
    
    @Override
    public String getUSDTBalance(String name) {
        AccountBinance account = accountBinanceRepository.findByName(name);
        if (account != null) {
            // Llamamos al servicio de Binance para obtener el balance de USDT de la cuenta
            return binanceService.getGeneralBalance(name);  // Método que consulta el balance de USDT
        }
        return "{\"error\": \"Cuenta no encontrada.\"}";
    }
    
    @Override
    public Double getEstimatedUSDTBalance(String name) {
        // Aquí delegamos al método que calcula sumando cada activo * precio USDT
        return binanceService.getEstimatedSpotBalance(name);
    }
    @Override
    public Double getFundingUSDTBalance(String name) {
        // delegamos al BinanceService
        return binanceService.getFundingAssetBalance(name, "USDT");
    }

}
