package com.binance.web.SellDollars;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SellDollarsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SellDollarsServiceImpl implements SellDollarsService{
	
	@Autowired
    private SellDollarsRepository sellDollarsRepository;
	
	@Autowired
    private AccountBinanceRepository accountBinanceRepository;

	@Override
	@Transactional
	public SellDollars createSellDollars(SellDollarsDto dto) {
	    // Obtener la cuenta de Binance correspondiente
	    AccountBinance accountBinance = accountBinanceRepository.findById(dto.getAccountBinanceId())
	            .orElseThrow(() -> new RuntimeException("AccountBinance not found"));

	    // Crear una nueva venta de dólares
	    SellDollars sale = new SellDollars();
	    sale.setIdWithdrawals(dto.getIdWithdrawals());
	    sale.setTasa(dto.getTasa());
	    sale.setDollars(dto.getDollars());
	    sale.setDate(dto.getDate());
	    sale.setNameAccount(dto.getNameAccount());
	    sale.setAccountBinance(accountBinance);  // Asociar la cuenta de Binance con la venta

	    return sellDollarsRepository.save(sale);  // Guardar la venta de dólares
	}

}
