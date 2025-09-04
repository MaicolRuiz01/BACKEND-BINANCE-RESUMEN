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

    /**
     * Actualiza el balance de una cripto específica en una cuenta.
     * @param account   La cuenta de Binance
     * @param symbol    El símbolo de la cripto ("USDT", "TRX", "BTC", etc.)
     * @param delta     El cambio en el balance (positivo = sumar, negativo = restar)
     */
    @Transactional
    public void updateCryptoBalance(AccountBinance account, String symbol, Double delta) {
        AccountCryptoBalance cb = account.getCryptoBalances().stream()
            .filter(c -> c.getCryptoSymbol().equalsIgnoreCase(symbol))
            .findFirst()
            .orElse(null);

        if (cb != null) {
            double nuevoSaldo = (cb.getBalance() != null ? cb.getBalance() : 0.0) + delta;
            if (nuevoSaldo < 0) {
                throw new RuntimeException("Saldo insuficiente en " + symbol + " para la cuenta " + account.getName());
            }
            cb.setBalance(nuevoSaldo);
        } else {
            if (delta < 0) {
                throw new RuntimeException("No existe balance en " + symbol + " para descontar");
            }
            cb = new AccountCryptoBalance();
            cb.setCryptoSymbol(symbol);
            cb.setBalance(delta);
            cb.setAccountBinance(account);
            account.getCryptoBalances().add(cb);
        }

        accountBinanceRepository.save(account); // cascada actualiza también balances
    }

}
