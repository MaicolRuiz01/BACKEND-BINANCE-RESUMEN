package com.binance.web.serviceImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SaleP2pAccountCop;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.SaleP2PRepository;
import com.binance.web.service.AccountCopService;

@Service
public class AccountCopServiceImpl implements AccountCopService {

	private final AccountCopRepository AccountCopRepository;
	private final SaleP2PRepository saleP2PRepository; 

	public AccountCopServiceImpl(AccountCopRepository AccountCopRepository, SaleP2PRepository saleP2PRepository) {
	    this.AccountCopRepository = AccountCopRepository;
	    this.saleP2PRepository = saleP2PRepository;
	}

	@Override
	public List<AccountCop> findAllAccountCop() {
		List<AccountCop> cuentasCop = AccountCopRepository.findAll();
		return cuentasCop;
	}

	@Override
	public AccountCop findByIdAccountCop(Integer id) {
		return AccountCopRepository.findById(id).orElse(null);

	}


	@Override
	public void saveAccountCop(AccountCop accountCop) {
	    // Verificar si los campos obligatorios no son nulos o inválidos
	    if (accountCop.getName() == null || accountCop.getBalance() == null) {
	        throw new IllegalArgumentException("El nombre de la cuenta y el saldo no pueden ser nulos.");
	    }
	    accountCop.setSaldoInicialDelDia(accountCop.getBalance());
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
	
	@Override
	public List<SaleP2P> getSalesByAccountCopId(Integer accountCopId) {
	    AccountCop accountCop = AccountCopRepository.findById(accountCopId).orElse(null);
	    if (accountCop == null) {
	        return Collections.emptyList(); // O lanza una excepción personalizada
	    }

	    List<SaleP2P> sales = new ArrayList<>();
	    for (SaleP2pAccountCop detail : accountCop.getSaleP2pDetails()) {
	        SaleP2P sale = detail.getSaleP2p();
	        if (sale != null) {
	            sales.add(sale);
	        }
	    }

	    return sales;
	}

	
}
