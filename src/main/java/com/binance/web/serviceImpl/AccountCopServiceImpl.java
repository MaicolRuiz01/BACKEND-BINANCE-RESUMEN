package com.binance.web.serviceImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
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
	    if (accountCop.getName() == null || accountCop.getBalance() == null) {
	        throw new IllegalArgumentException("El nombre de la cuenta y el saldo no pueden ser nulos.");
	    }

	    

	    accountCop.setSaldoInicialDelDia(accountCop.getBalance());
	    AccountCopRepository.save(accountCop);
	}

	@Override
	public void updateAccountCop(Integer id, AccountCop accountCop) {
	    AccountCop existing = AccountCopRepository.findById(id).orElse(null);
	    if (existing == null) {
	        throw new IllegalArgumentException("La cuenta con el ID " + id + " no existe.");
	    }

	    existing.setName(accountCop.getName());
	    existing.setBalance(accountCop.getBalance());

	    // 👇 actualizar bankType si viene
	    if (accountCop.getBankType() != null) {
	        existing.setBankType(accountCop.getBankType());
	    }

	    AccountCopRepository.save(existing);
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
