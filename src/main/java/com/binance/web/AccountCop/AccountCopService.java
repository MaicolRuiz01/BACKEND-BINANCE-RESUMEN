package com.binance.web.AccountCop;

import java.util.List;

import com.binance.web.Entity.AccountCop;

public interface AccountCopService {
	List<AccountCop> findAllAccountCop();
	AccountCop findByIdAccountCop(Integer id);
    void saveAccountCop(AccountCop AccountCop);
    void updateAccountCop(Integer id, AccountCop AccountCop);
    void deleteAccountCop(Integer id);
}
