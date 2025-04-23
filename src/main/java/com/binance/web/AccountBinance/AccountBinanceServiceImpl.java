package com.binance.web.AccountBinance;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AccountBinanceServiceImpl implements AccountBinanceService {

    private final AccountBinanceRepository accountBinanceRepository;

    public AccountBinanceServiceImpl(AccountBinanceRepository accountBinanceRepository) {
        this.accountBinanceRepository = accountBinanceRepository;
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
            accountBinanceRepository.save(existing);
        }
    }

    @Override
    public void deleteAccountBinance(Integer id) {
        accountBinanceRepository.deleteById(id);
    }
}
