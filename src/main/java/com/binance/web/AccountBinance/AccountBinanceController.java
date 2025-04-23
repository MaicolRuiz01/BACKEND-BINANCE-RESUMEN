package com.binance.web.AccountBinance;

import com.binance.web.BinanceAPI.BinanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cuenta-binance")
public class AccountBinanceController {

    private final AccountBinanceService accountBinanceService;
    private final BinanceService binanceService;

    @Autowired
    public AccountBinanceController(AccountBinanceService accountBinanceService, BinanceService binanceService) {
        this.accountBinanceService = accountBinanceService;
        this.binanceService = binanceService;
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

    // 🔹 Obtener saldo spot de una cuenta por nombre
    @GetMapping("/saldo-spot/{nombre}")
    public ResponseEntity<String> getSpotBalance(@PathVariable String nombre) {
        String response = binanceService.getSpotBalances(nombre.toUpperCase());
        return ResponseEntity.ok(response);
    }

    // 🔹 Obtener saldo de futuros de una cuenta por nombre
    @GetMapping("/saldo-futures/{nombre}")
    public ResponseEntity<String> getFuturesBalance(@PathVariable String nombre) {
        String response = binanceService.getFuturesBalances(nombre.toUpperCase());
        return ResponseEntity.ok(response);
    }
}
