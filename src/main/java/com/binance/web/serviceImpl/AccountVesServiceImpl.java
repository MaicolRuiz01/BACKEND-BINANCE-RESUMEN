package com.binance.web.serviceImpl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountVes;
import com.binance.web.Repository.AccountVesRepository;
import com.binance.web.service.AccountVesService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountVesServiceImpl implements AccountVesService {

    private final AccountVesRepository repo;

    @Override
    public List<AccountVes> findAll() {
        return repo.findAll();
    }

    @Override
    public List<AccountVes> findAllAccountVes() {
        return repo.findAll();
    }

    // ✔ BUSCAR POR ID
    @Override
    public AccountVes findById(Integer id) {
        return repo.findById(id).orElse(null);
    }

    @Override
    public AccountVes findByIdAccountVes(Integer id) {
        return repo.findById(id).orElse(null);
    }

    // ✔ CREAR CUENTA
    @Override
    public void save(AccountVes acc) {
        if (acc.getBalance() == null) acc.setBalance(0.0);
        acc.setSaldoInicialDelDia(acc.getBalance());
        repo.save(acc);
    }

    @Override
    public void saveAccountVes(AccountVes acc) {
        save(acc);
    }

    // ✔ ACTUALIZAR
    @Override
    public void update(Integer id, AccountVes acc) {
        AccountVes existing = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Cuenta VES no encontrada: " + id));

        existing.setName(acc.getName());
        existing.setBalance(acc.getBalance());
        repo.save(existing);
    }

    @Override
    public void updateAccountVes(Integer id, AccountVes acc) {
        update(id, acc);
    }

    // ✔ ELIMINAR
    @Override
    public void delete(Integer id) {
        repo.deleteById(id);
    }

    @Override
    public void deleteAccountVes(Integer id) {
        repo.deleteById(id);
    }

    // ✔ TOTAL DE SALDO
    @Override
    public Double getTotalSaldoVes() {
        Double total = repo.sumTotalBalance();
        return total != null ? total : 0.0;
    }
    
}

