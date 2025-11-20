package com.binance.web.efectivo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.Efectivo;
import com.binance.web.Repository.EfectivoRepository;

@Service
public class EfectivoServiceImpl implements EfectivoService{
	
	@Autowired
    private EfectivoRepository efectivoRepo;

    @Override
    public Efectivo crearCaja(Efectivo caja) {
        caja.setId(null); // que sea nueva

        if (caja.getSaldo() == null) {
            caja.setSaldo(0.0);
        }

        // 👉 saldo inicial del día al momento de crear
        caja.setSaldoInicialDelDia(caja.getSaldo());

        return efectivoRepo.save(caja);
    }
	
	

}
