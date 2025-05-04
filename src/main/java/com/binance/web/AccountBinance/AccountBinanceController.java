package com.binance.web.AccountBinance;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.Entity.AccountBinance;

@RestController
@RequestMapping("/cuenta-binance")
public class AccountBinanceController {

    private final AccountBinanceService accountBinanceService;

    public AccountBinanceController(AccountBinanceService accountBinanceService) {
        this.accountBinanceService = accountBinanceService;
    }

    @GetMapping
    public ResponseEntity<List<AccountBinance>> getAllAccounts() {
        return ResponseEntity.ok(accountBinanceService.findAllAccountBinance());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountBinance> getAccountById(@PathVariable Integer id) {
        AccountBinance account = accountBinanceService.findByIdAccountBinance(id);
        return account != null ? ResponseEntity.ok(account) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<AccountBinance> createAccount(@RequestBody AccountBinance accountBinance) {
        accountBinanceService.saveAccountBinance(accountBinance);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountBinance);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountBinance> updateAccount(@PathVariable Integer id, @RequestBody AccountBinance accountBinance) {
        AccountBinance existing = accountBinanceService.findByIdAccountBinance(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        accountBinanceService.updateAccountBinance(id, accountBinance);
        return ResponseEntity.ok(accountBinance);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Integer id) {
        accountBinanceService.deleteAccountBinance(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/buscar")
    public ResponseEntity<AccountBinance> getAccountByName(@RequestParam String name) {
        AccountBinance account = accountBinanceService.findByName(name);
        return account != null ? ResponseEntity.ok(account) : ResponseEntity.notFound().build();
    }
    
    @GetMapping("/balance")
    public ResponseEntity<Double> getBalanceByName(@RequestParam String name) {
        System.out.println("🟢 Consultando balance de: " + name);  // <- agrega esto para probar si entra
        AccountBinance account = accountBinanceService.findByName(name);
        return account != null ? ResponseEntity.ok(account.getBalance()) : ResponseEntity.notFound().build();
    }
}
