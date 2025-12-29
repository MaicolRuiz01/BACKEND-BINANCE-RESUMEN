package com.binance.web.service;

import java.util.List;

import com.binance.web.Entity.AccountVes;

public interface AccountVesService {
    List<AccountVes> findAll();
    AccountVes findById(Integer id);
    void save(AccountVes acc);
    void update(Integer id, AccountVes acc);
    void delete(Integer id);
    Double getTotalSaldoVes();
    List<AccountVes> findAllAccountVes();

    AccountVes findByIdAccountVes(Integer id);

    void saveAccountVes(AccountVes accountVes);

    void updateAccountVes(Integer id, AccountVes accountVes);

    void deleteAccountVes(Integer id);
}
