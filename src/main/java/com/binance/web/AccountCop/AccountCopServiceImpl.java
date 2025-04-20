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
	public void saveAccountCop(AccountCop accountCop) {
	    // Verificar si los campos obligatorios no son nulos o inválidos
	    if (accountCop.getName() == null || accountCop.getBalance() == null) {
	        throw new IllegalArgumentException("El nombre de la cuenta y el saldo no pueden ser nulos.");
	    }
	    // Guardar la cuenta en el repositorio
	    AccountCopRepository.save(accountCop);
	}

	@Override
	public void updateAccountCop(Integer id, AccountCop accountCop) {
	    // Verificar si la cuenta existe
	    AccountCop existingAccountCop = AccountCopRepository.findById(id).orElse(null);
	    if (existingAccountCop == null) {
	        throw new IllegalArgumentException("La cuenta con el ID " + id + " no existe.");
	    }

	    // Actualizar solo los campos modificados
	    existingAccountCop.setName(accountCop.getName());
	    existingAccountCop.setBalance(accountCop.getBalance());

	    // Guardar la cuenta actualizada
	    AccountCopRepository.save(existingAccountCop);
	}


	@Override
	public void deleteAccountCop(Integer id) {
		AccountCopRepository.deleteById(id);
	}

}
