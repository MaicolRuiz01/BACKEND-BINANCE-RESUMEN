package com.binance.web.accountCryptoBalance;

import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCryptoBalance;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.AccountCryptoBalanceRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountCryptoBalanceService {

    private final AccountBinanceRepository accountBinanceRepository;
    private final AccountCryptoBalanceRepository accountCryptoBalanceRepository;

    // Estricto (no permite negativos)
    @Transactional
    public void updateCryptoBalance(AccountBinance account, String symbol, Double delta) {
        updateCryptoBalance(account, symbol, delta, false);
    }

    // Permite negativos cuando allowNegative = true
    @Transactional
    public void updateCryptoBalance(AccountBinance account, String symbol, Double delta, boolean allowNegative) {
        if (account == null) throw new IllegalArgumentException("AccountBinance es null");
        String sym = (symbol == null ? "USDT" : symbol.trim().toUpperCase());

        AccountCryptoBalance cb = accountCryptoBalanceRepository
                .findByAccountBinanceIdAndCryptoSymbol(account.getId(), sym)
                .orElse(null);

        if (cb == null) {
            cb = new AccountCryptoBalance();
            cb.setAccountBinance(account);
            cb.setCryptoSymbol(sym);
            cb.setBalance(0.0);
        }

        double current = cb.getBalance() == null ? 0.0 : cb.getBalance();
        double next = current + (delta == null ? 0.0 : delta);

        if (!allowNegative && next < -1e-6) {
            throw new RuntimeException("Saldo insuficiente en " + sym + " para la cuenta " + account.getName());
        }

        cb.setBalance(next);
        accountCryptoBalanceRepository.save(cb);
    }
}
