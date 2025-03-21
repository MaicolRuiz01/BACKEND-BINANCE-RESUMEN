package com.binance.web.AccountCop;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class AccountCopServiceImpl implements AccountCopService {

	private final AccountCopRepository AccountCopRepository;

	public AccountCopServiceImpl(AccountCopRepository AccountCopRepository) {
	    this.AccountCopRepository = AccountCopRepository;
	}

	@Override
	public List<AccountCop> findAllAccountCop() {
		List<AccountCop> cuentasCop = AccountCopRepository.findAll();
		return cuentasCop;
	}

	@Override
	public AccountCop findByIdAccountCop(Integer id) {
		AccountCop AccountCop = AccountCopRepository.findById(id).get();
		return AccountCop;
	}

	@Override
	public void saveAccountCop(AccountCop AccountCop) {
		AccountCopRepository.save(AccountCop);
	}

	@Override
	public void updateAccountCop(Integer id, AccountCop cuenta) {
		AccountCop AccountCop = AccountCopRepository.findById(id).orElse(null);
		AccountCopRepository.save(AccountCop);
	}

	@Override
	public void deleteAccountCop(Integer id) {
		AccountCopRepository.deleteById(id);
	}

}
