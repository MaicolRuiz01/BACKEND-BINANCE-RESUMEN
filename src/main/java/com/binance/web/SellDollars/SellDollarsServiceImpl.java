package com.binance.web.SellDollars;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.binance.web.SaleP2P.AssignAccountDto;

import com.binance.web.AccountCop.AccountCopService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.SellDollarsAccountCop;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.Entity.Supplier;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SellDollarsServiceImpl implements SellDollarsService{
	
	@Autowired
    private SellDollarsRepository sellDollarsRepository;
	
	@Autowired
    private AccountBinanceRepository accountBinanceRepository;
	@Autowired
	private SupplierRepository supplierRepository;
	@Autowired
	private AccountCopService accountCopService; // para obtener y actualizar balances

	@Override
	@Transactional
	public SellDollars createSellDollars(SellDollarsDto dto) {
	    AccountBinance accountBinance = accountBinanceRepository.findById(dto.getAccountBinanceId())
	            .orElseThrow(() -> new RuntimeException("AccountBinance not found"));

	    Supplier supplier = supplierRepository.findById(dto.getSupplier())
	            .orElseThrow(() -> new RuntimeException("Supplier no encontrado"));

	    SellDollars sale = new SellDollars();
	    sale.setIdWithdrawals(dto.getIdWithdrawals());
	    sale.setTasa(dto.getTasa());
	    sale.setDollars(dto.getDollars());
	    sale.setDate(dto.getDate());
	    sale.setNameAccount(dto.getNameAccount());
	    sale.setAccountBinance(accountBinance);
	    sale.setPesos(dto.getPesos());
	    sale.setSupplier(supplier);

	    // Restar dólares y pesos
	    accountBinance.setBalance((accountBinance.getBalance() != null ? accountBinance.getBalance() : 0.0) - dto.getDollars());
	    supplier.setBalance((supplier.getBalance() != null ? supplier.getBalance() : 0.0) - dto.getPesos());
	    
	    // Manejo de cuentas COP asignadas
	    List<SellDollarsAccountCop> detalles = new ArrayList<>();
	    if (dto.getAccounts() != null) {
	        for (AssignAccountDto assignDto : dto.getAccounts()) {
	            AccountCop acc = accountCopService.findByIdAccountCop(assignDto.getAccountCop());
	            acc.setBalance((acc.getBalance() != null ? acc.getBalance() : 0.0) + assignDto.getAmount());
	            accountCopService.saveAccountCop(acc);

	            SellDollarsAccountCop detalle = new SellDollarsAccountCop();
	            detalle.setSellDollars(sale);
	            detalle.setAccountCop(acc);
	            detalle.setAmount(assignDto.getAmount());
	            detalle.setNameAccount(assignDto.getNameAccount());

	            detalles.add(detalle);
	        }
	    }

	    sale.setSellDollarsAccounts(detalles); // ← Relación en SellDollars

	    accountBinanceRepository.save(accountBinance);
	    supplierRepository.save(supplier);

	    return sellDollarsRepository.save(sale);
	}


}
