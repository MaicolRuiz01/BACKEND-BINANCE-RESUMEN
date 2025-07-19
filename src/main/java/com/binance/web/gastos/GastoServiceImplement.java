package com.binance.web.gastos;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.Gasto;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.GastoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class GastoServiceImplement implements GastoService{

    @Autowired
    private GastoRepository gastoRepository;
    @Autowired
    private AccountCopRepository accountCopRepository;
    @Autowired
    private EfectivoRepository efectivoRepository;

    public List<Gasto> findAll() {
        return gastoRepository.findAll();
    }

    public Optional<Gasto> findById(Integer id) {
        return gastoRepository.findById(id);
    }

    public Gasto save(Gasto gasto) {
        return gastoRepository.save(gasto);
    }

    public void deleteById(Integer id) {
        gastoRepository.deleteById(id);
    }

    @Override
    public Gasto saveGasto(Gasto nuevoGasto) {
        Double monto = nuevoGasto.getMonto();
        Double montoComision = monto * 1.004;

        // Siempre asigna la fecha del sistema
        nuevoGasto.setFecha(LocalDateTime.now());

        if (nuevoGasto.getCuentaPago() != null) {
            AccountCop cuentaPago = accountCopRepository.findById(nuevoGasto.getCuentaPago().getId())
                .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));

            cuentaPago.setBalance(cuentaPago.getBalance() - montoComision);
            accountCopRepository.save(cuentaPago);
        }

        if (nuevoGasto.getPagoEfectivo() != null) {
            Efectivo caja = efectivoRepository.findById(nuevoGasto.getPagoEfectivo().getId())
                .orElseThrow(() -> new RuntimeException("Caja no encontrada"));

            caja.setSaldo(caja.getSaldo() - monto);
            efectivoRepository.save(caja);
        }

        return gastoRepository.save(nuevoGasto);
    }

}
