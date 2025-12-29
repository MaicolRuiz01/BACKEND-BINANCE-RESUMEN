package com.binance.web.service;

import java.util.List;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.SaleP2P;

public interface AccountCopService {
	List<AccountCop> findAllAccountCop();
	AccountCop findByIdAccountCop(Integer id);
    void saveAccountCop(AccountCop AccountCop);
    void updateAccountCop(Integer id, AccountCop AccountCop);
    void deleteAccountCop(Integer id);
    List<SaleP2P> getSalesByAccountCopId(Integer accountCopId);

}
