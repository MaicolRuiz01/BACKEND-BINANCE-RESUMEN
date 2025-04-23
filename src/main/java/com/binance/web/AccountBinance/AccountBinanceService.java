package com.binance.web.AccountBinance;

import java.util.List;

public interface AccountBinanceService {
    List<AccountBinance> findAllAccountBinance();
    AccountBinance findByIdAccountBinance(Integer id);
    void saveAccountBinance(AccountBinance accountBinance);
    void updateAccountBinance(Integer id, AccountBinance accountBinance);
    void deleteAccountBinance(Integer id);
}
