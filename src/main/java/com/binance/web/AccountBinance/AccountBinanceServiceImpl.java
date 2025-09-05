package com.binance.web.AccountBinance;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.BinanceAPI.TronScanService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCryptoBalance;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.PurchaseRate;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.AccountCryptoBalanceRepository;
import com.binance.web.Repository.AverageRateRepository;
import com.binance.web.Repository.PurchaseRateRepository;
import com.binance.web.balance.PurchaseRate.PurchaseRateService;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountBinanceServiceImpl implements AccountBinanceService {

    private final BinanceService binanceService;
    private final AccountBinanceRepository accountBinanceRepository;
    private final TronScanService tronScanService;
    @Autowired
    private AverageRateRepository averageRateRepository;
    @Autowired
    private AccountCryptoBalanceRepository accountCryptoBalanceRepository;


    public AccountBinanceServiceImpl(
            AccountBinanceRepository accountBinanceRepository,
            BinanceService binanceService,
            TronScanService tronScanService) {
        this.accountBinanceRepository = accountBinanceRepository;
        this.binanceService = binanceService;
        this.tronScanService = tronScanService;

    }
    
    private AccountCryptoBalance findOrCreate(AccountBinance account, String cryptoSymbol) {
        return accountCryptoBalanceRepository
            .findByAccountBinance_IdAndCryptoSymbol(account.getId(), cryptoSymbol)
            .orElseGet(() -> {
                AccountCryptoBalance b = new AccountCryptoBalance();
                b.setAccountBinance(account);
                b.setCryptoSymbol(cryptoSymbol);
                b.setBalance(0.0);
                return accountCryptoBalanceRepository.save(b);
            });
    }
    
    @Override
    public void updateOrCreateCryptoBalance(Integer accountId, String cryptoSymbol, Double delta) {
        if (accountId == null || cryptoSymbol == null || delta == null) return;

        // Proxy sin ir a DB (no accedas a propiedades del account)
        AccountBinance accountRef = accountBinanceRepository.getReferenceById(accountId);

        AccountCryptoBalance bal = accountCryptoBalanceRepository
            .findByAccountBinance_IdAndCryptoSymbol(accountId, cryptoSymbol)
            .orElseGet(() -> {
                AccountCryptoBalance b = new AccountCryptoBalance();
                b.setAccountBinance(accountRef);
                b.setCryptoSymbol(cryptoSymbol.toUpperCase());
                b.setBalance(0.0);
                return b;
            });

        double current = bal.getBalance() != null ? bal.getBalance() : 0.0;
        bal.setBalance(current + delta);
        accountCryptoBalanceRepository.save(bal);
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
    
    /**
     * @deprecated Antes restaba el "balance" en USD de AccountBinance.
     * Ahora redirige a USDT por compatibilidad. Cambia las llamadas a updateOrCreateCryptoBalance/subtractCryptoBalance.
     */
    @Override
    @Deprecated
    public void subtractBalance(String name, Double amount) {
        if (amount == null || amount <= 0) return;

        AccountBinance account = accountBinanceRepository.findByName(name);
        if (account == null) {
            throw new RuntimeException("Account not found: " + name);
        }

        // Por compatibilidad: asumimos que el viejo "balance" era USDT
        updateOrCreateCryptoBalance(account.getId(), "USDT", -Math.abs(amount));
        System.out.println("🔴 Restando " + amount + " USDT a cuenta: " + name);
    }

    
    @Override
    public Double getTotalExternalBalance() {
        List<AccountBinance> cuentas = accountBinanceRepository.findAll();
        double total = 0.0;

        for (AccountBinance account : cuentas) {
            try {
                if ("BINANCE".equalsIgnoreCase(account.getTipo())) {
                    // Llamamos a la versión robusta del balance USDT
                    Double balance = binanceService.getGeneralBalanceInUSDT(account.getName());
                    total += balance != null ? balance : 0.0;
                } else if ("TRUST".equalsIgnoreCase(account.getTipo())) {
                    total += tronScanService.getTotalAssetTokenOverview(account.getAddress());
                }
            } catch (Exception e) {
                System.out.println("⚠️ Error con cuenta " + account.getName() + ": " + e.getMessage());
                // Continuamos con las demás cuentas
            }
        }

        return total;
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

 // ✅ Nuevo método para sustraer un balance de cripto
    @Override
    public void subtractCryptoBalance(Integer accountId, String cryptoSymbol, Double amount) {
        if (amount == null || amount <= 0) return;
        updateOrCreateCryptoBalance(accountId, cryptoSymbol, -Math.abs(amount));
    }

    // ✅ Obtiene balance de una cripto por nombre de cuenta
    @Override
    public Double getCryptoBalance(String accountName, String cryptoSymbol) {
        AccountBinance account = accountBinanceRepository.findByName(accountName);
        if (account == null) return null;

        return account.getCryptoBalances().stream()
            .filter(b -> cryptoSymbol.equalsIgnoreCase(b.getCryptoSymbol()))
            .map(AccountCryptoBalance::getBalance)
            .findFirst()
            .orElse(0.0);
    }

    // ✅ Modificar este método para que use la nueva tabla
    @Override
    public Double getUSDTBalance(String name) {
        AccountBinance account = accountBinanceRepository.findByName(name);
        if (account == null) {
            return null;
        }
        
        String tipo = account.getTipo();
        if ("BINANCE".equalsIgnoreCase(tipo)) {
            // ✅ Aquí se mantiene la llamada al servicio externo
            try {
                return binanceService.getGeneralBalanceInUSDT(name);
            } catch (Exception e) {
                return 0.0;
            }
        } else if ("TRUST".equalsIgnoreCase(tipo)) {
            // ✅ Aquí se mantiene la llamada al servicio externo
            return tronScanService.getTotalAssetTokenOverview(account.getAddress());
        } else {
            return 0.0;
        }
    }
    
    @Override
    public BigDecimal getTotalBalance() {
        List<AccountBinance> accounts = accountBinanceRepository.findAll();
        
        BigDecimal totalBalance = accounts.stream()
            .flatMap(a -> a.getCryptoBalances().stream())
            .map(AccountCryptoBalance::getBalance)
            .filter(Objects::nonNull)
            .map(BigDecimal::valueOf)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        AverageRate rate = averageRateRepository.findTopByOrderByIdDesc()
            .orElseThrow(() -> new RuntimeException("No purchase rate available"));

        if (rate.getAverageRate() == null) {
            throw new RuntimeException("No purchase rate available");
        }
        
        return totalBalance.multiply(BigDecimal.valueOf(rate.getAverageRate()));
    }
    
    // ✅ Modificar este método para usar la nueva tabla
    @Override
    public BigDecimal getTotalBalanceInterno() {
        return accountBinanceRepository.findAll().stream()
            .flatMap(a -> a.getCryptoBalances().stream())
            .map(AccountCryptoBalance::getBalance)
            .filter(Objects::nonNull)
            .map(BigDecimal::valueOf)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private double priceInUSDT(String symbol) {
        if (symbol == null) return 0.0;
        String s = symbol.trim().toUpperCase();
        if ("USDT".equals(s) || "USDC".equals(s)) return 1.0; // stables 1:1
        try {
            // Usa un método del BinanceService que devuelva el precio spot en USDT.
            // Si no lo tienes, crea uno público en BinanceService que llame a /api/v3/ticker/price.
            Double px = binanceService.getPriceInUSDT(s);
            return px != null ? px : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    // Balance interno de UNA cuenta, convertido a USD
    @Override
    public Double getInternalUsdBalance(String accountName) {
        AccountBinance acc = accountBinanceRepository.findByName(accountName);
        if (acc == null) return 0.0;

        double sum = 0.0;
        for (AccountCryptoBalance b : acc.getCryptoBalances()) {
            if (b == null) continue;
            double qty = b.getBalance() != null ? b.getBalance() : 0.0;
            if (qty <= 0) continue;
            String symbol = b.getCryptoSymbol();
            double px = priceInUSDT(symbol);
            sum += qty * px;
        }
        return sum;
    }

    // (Opcional) Suma de TODAS las cuentas, convertido a USD
    @Override
    public Double getTotalInternalUsdBalance() {
        return accountBinanceRepository.findAll().stream()
            .flatMap(a -> a.getCryptoBalances().stream())
            .filter(Objects::nonNull)
            .mapToDouble(b -> {
                double qty = b.getBalance() != null ? b.getBalance() : 0.0;
                double px  = priceInUSDT(b.getCryptoSymbol());
                return qty * px;
            })
            .sum();
    }
    
 // AccountBinanceServiceImpl.java

    @Override
    @Transactional
    public void syncInternalBalancesFromExchange(String accountName) {
        AccountBinance account = accountBinanceRepository.findByName(accountName);
        if (account == null) throw new RuntimeException("Cuenta no encontrada: " + accountName);
        if (!"BINANCE".equalsIgnoreCase(account.getTipo())) {
            throw new RuntimeException("Solo soportado para cuentas BINANCE por ahora");
        }

        // 1) Traer snapshot externo (Spot + Funding)
        Map<String, Double> external = binanceService.getAllBalancesByAsset(accountName);

        // 2) Indexar existentes
        Map<String, AccountCryptoBalance> existing = account.getCryptoBalances().stream()
                .collect(Collectors.toMap(b -> b.getCryptoSymbol().toUpperCase(), b -> b));

        // 3) Actualizar / crear
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, Double> e : external.entrySet()) {
            String sym = e.getKey().toUpperCase();
            double qty = e.getValue();

            AccountCryptoBalance bal = existing.get(sym);
            if (bal == null) {
                bal = new AccountCryptoBalance();
                bal.setAccountBinance(account);
                bal.setCryptoSymbol(sym);
                bal.setBalance(qty);
                account.getCryptoBalances().add(bal);
            } else {
                bal.setBalance(qty);
            }
            seen.add(sym);
        }

        // 4) Eliminar las que ya no existen en el exchange (por snapshot real)
        account.getCryptoBalances().removeIf(b -> !seen.contains(b.getCryptoSymbol().toUpperCase()));

        accountBinanceRepository.save(account);
    }

    @Override
    @Transactional
    public void syncAllInternalBalancesFromExchange() {
        List<AccountBinance> accounts = accountBinanceRepository.findAll().stream()
                .filter(a -> "BINANCE".equalsIgnoreCase(a.getTipo()))
                .collect(Collectors.toList());
        for (AccountBinance a : accounts) {
            syncInternalBalancesFromExchange(a.getName());
        }
    }
 // AccountBinanceServiceImpl.java
    @Override
    @Transactional(readOnly = true)
    public Map<String, Double> getExternalBalancesSnapshot(String name) {
        AccountBinance acc = accountBinanceRepository.findByName(name);
        if (acc == null) throw new RuntimeException("Cuenta no encontrada: " + name);

        String tipo = acc.getTipo() != null ? acc.getTipo().toUpperCase() : "";
        switch (tipo) {
            case "BINANCE":
                return binanceService.getAllBalancesByAsset(name); // ya lo tienes
            case "TRUST":
            case "TRUSTWALLET":
                String addr = acc.getAddress();
                if (addr == null || addr.isBlank()) throw new RuntimeException("Wallet address vacío para " + name);
                return tronScanService.getBalancesByAsset(addr);
            default:
                return Collections.emptyMap();
        }
    }
    
    @Override
    @Transactional
    public Map<String, Double> syncInternalBalancesFromExternal(String name) {
        Map<String, Double> snapshot = getExternalBalancesSnapshot(name);
        AccountBinance acc = accountBinanceRepository.findByName(name);
        if (acc == null) throw new RuntimeException("Cuenta no encontrada: " + name);

        // Actualiza/crea cada cripto
        for (Map.Entry<String, Double> e : snapshot.entrySet()) {
            AccountCryptoBalance b = findOrCreate(acc, e.getKey());
            b.setBalance(e.getValue());
            accountCryptoBalanceRepository.save(b);
        }
        // (Opcional) elimina internas que ya no existan externamente
        List<AccountCryptoBalance> actuales = accountCryptoBalanceRepository.findByAccountBinance_Id(acc.getId());
        Set<String> vigentes = snapshot.keySet().stream().map(String::toUpperCase).collect(Collectors.toSet());
        for (AccountCryptoBalance b : actuales) {
            if (!vigentes.contains(b.getCryptoSymbol().toUpperCase())) {
                accountCryptoBalanceRepository.delete(b);
            }
        }
        return snapshot;
    }
    @Override
    @Transactional
    public void syncAllInternalBalancesFromExternal() {
        accountBinanceRepository.findAll().forEach(acc -> {
            try {
                syncInternalBalancesFromExternal(acc.getName());
            } catch (Exception e) {
                System.out.println("⚠️ No se pudo sincronizar " + acc.getName() + ": " + e.getMessage());
            }
        });
    }

}
