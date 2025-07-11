package com.binance.web.AccountBinance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.BinanceAPI.TronScanService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.PurchaseRate;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.PurchaseRateRepository;
import com.binance.web.balance.PurchaseRate.PurchaseRateService;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class AccountBinanceServiceImpl implements AccountBinanceService {

    private final BinanceService binanceService;
    private final AccountBinanceRepository accountBinanceRepository;
    private final TronScanService tronScanService;


    public AccountBinanceServiceImpl(
            AccountBinanceRepository accountBinanceRepository,
            BinanceService binanceService,
            TronScanService tronScanService) {
        this.accountBinanceRepository = accountBinanceRepository;
        this.binanceService = binanceService;
        this.tronScanService = tronScanService;

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
        if (account == null) {
            return "{\"error\": \"Cuenta no encontrada.\"}";
        }

        String tipo = account.getTipo();
        if ("BINANCE".equalsIgnoreCase(tipo)) {
            // Cuenta Binance: delegamos al servicio de Binance
            return binanceService.getGeneralBalance(name);
        } else if ("TRUST".equalsIgnoreCase(tipo)) {
            // Cuenta TRON/TRUST: usamos TronScanService para obtener total assets en USD
            double balanceUsd = tronScanService.getTotalAssetTokenOverview(account.getAddress());
            return String.valueOf(balanceUsd);
        } else {
            // Tipo no reconocido
            return "{\"error\": \"Tipo de cuenta no soportado: " + tipo + "\"}";
        }
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

    @Autowired
    private PurchaseRateRepository purchaseRateRepository;

    @Override
    public BigDecimal getTotalBalance() {
        List<AccountBinance> accounts = accountBinanceRepository.findAll();

        BigDecimal totalBalance = accounts.stream()
                .map(AccountBinance::getBalance)
                .filter(Objects::nonNull)
                .map(BigDecimal::valueOf) // convierte Double a BigDecimal
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PurchaseRate latestRate = purchaseRateRepository.findTopByOrderByDateDesc();

        if (latestRate == null || latestRate.getRate() == null) {
            throw new RuntimeException("No purchase rate available");
        }

        return totalBalance.multiply(BigDecimal.valueOf(latestRate.getRate()));
    }
    
    

}
