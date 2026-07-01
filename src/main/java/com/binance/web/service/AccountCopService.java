package com.binance.web.service;

import java.util.List;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Repository.AccountCopRepository;

public interface AccountCopService {
	List<AccountCop> findAllAccountCop();
	/** Solo id + saldo — consulta liviana para refrescar el saldo rápido. */
	List<AccountCopRepository.SaldoView> findAllSaldos();
	AccountCop findByIdAccountCop(Integer id);
    void saveAccountCop(AccountCop AccountCop);
    void updateAccountCop(Integer id, AccountCop AccountCop);
    void deleteAccountCop(Integer id);
    List<SaleP2P> getSalesByAccountCopId(Integer accountCopId);
	void saveAccountCopSafe(AccountCop accountCop);
	String reconcileAccountCop(Integer accId);

}
