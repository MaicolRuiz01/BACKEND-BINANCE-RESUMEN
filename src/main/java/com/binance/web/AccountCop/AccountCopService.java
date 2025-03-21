package com.binance.web.AccountCop;

import java.util.List;

public interface AccountCopService {
	List<AccountCop> findAllAccountCop();
	AccountCop findByIdAccountCop(Integer id);
    void saveAccountCop(AccountCop AccountCop);
    void updateAccountCop(Integer id, AccountCop AccountCop);
    void deleteAccountCop(Integer id);
}
