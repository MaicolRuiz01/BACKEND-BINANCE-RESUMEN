package com.binance.web.AccountBinance;

import java.util.List;

import com.binance.web.Entity.AccountBinance;

public interface AccountBinanceService {
    List<AccountBinance> findAllAccountBinance();
    AccountBinance findByIdAccountBinance(Integer id);
    void saveAccountBinance(AccountBinance accountBinance);
    void updateAccountBinance(Integer id, AccountBinance accountBinance);
    void deleteAccountBinance(Integer id);
    AccountBinance findByName(String name);
    void subtractBalance(String name, Double amount);
}
