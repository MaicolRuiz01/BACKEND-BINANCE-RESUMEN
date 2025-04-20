package com.binance.web.SellDollars;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SellDollarsServiceImpl implements SellDollarsService{

	private final SellDollarsRepository repo;

    @Override
    @Transactional
    public SellDollars createSellDollars(SellDollarsDto dto) {
        SellDollars sale = new SellDollars();
        sale.setIdWithdrawals(dto.getIdWithdrawals());
        sale.setTasa(dto.getTasa());
        sale.setDollars(dto.getDollars());
        sale.setDate(dto.getDate());
        sale.setNameAccount(dto.getNameAccount());
        
        return repo.save(sale);
    }

}
