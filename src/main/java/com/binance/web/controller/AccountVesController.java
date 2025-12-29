package com.binance.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.AccountVes;
import com.binance.web.service.AccountVesService;

@RestController
@RequestMapping("/cuenta-ves")
public class AccountVesController {

    private final AccountVesService accountVesService;

    public AccountVesController(AccountVesService accountVesService) {
        this.accountVesService = accountVesService;
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<List<AccountVes>> getAllAccountVes() {
        List<AccountVes> cuentas = accountVesService.findAllAccountVes();
        return ResponseEntity.ok(cuentas);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountVes> getAccountVesById(@PathVariable Integer id) {
        AccountVes cuenta = accountVesService.findByIdAccountVes(id);
        return cuenta != null ? ResponseEntity.ok(cuenta) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<AccountVes> createAccountVes(@RequestBody AccountVes accountVes) {
        accountVesService.saveAccountVes(accountVes);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountVes);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountVes> updateAccountVes(
            @PathVariable Integer id,
            @RequestBody AccountVes accountVes) {

        AccountVes existing = accountVesService.findByIdAccountVes(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        accountVesService.updateAccountVes(id, accountVes);
        return ResponseEntity.ok(accountVes);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccountVes(@PathVariable Integer id) {
        accountVesService.deleteAccountVes(id);
        return ResponseEntity.noContent().build();
    }
}
