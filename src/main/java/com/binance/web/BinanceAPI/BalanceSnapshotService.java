package com.binance.web.BinanceAPI;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.SupplierRepository;

import jakarta.transaction.Transactional;

@Service
public class BalanceSnapshotService {

    @Autowired private ClienteRepository clienteRepo;
    @Autowired private SupplierRepository supplierRepo;
    @Autowired private EfectivoRepository efectivoRepo;
    @Autowired private AccountCopRepository accountCopRepo;

    // ✅ Ejecuta todos los días a las 00:00
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Bogota")
    @Transactional
    public void actualizarSaldosIniciales() {

        List<Cliente> clientes = clienteRepo.findAll();
        for (Cliente c : clientes) {
            c.setSaldoInicialDelDia(c.getSaldo());
        }
        clienteRepo.saveAll(clientes);

        List<Supplier> proveedores = supplierRepo.findAll();
        for (Supplier p : proveedores) {
            p.setSaldoInicialDelDia(p.getBalance());
        }
        supplierRepo.saveAll(proveedores);
        
     // 💵 CAJAS (EFECTIVO)
        List<Efectivo> cajas = efectivoRepo.findAll();
        for (Efectivo caja : cajas) {
            caja.setSaldoInicialDelDia(caja.getSaldo());
        }
        efectivoRepo.saveAll(cajas);

        // 🏦 CUENTAS COP
        List<AccountCop> cuentas = accountCopRepo.findAll();
        for (AccountCop acc : cuentas) {
            acc.setSaldoInicialDelDia(acc.getBalance());
        }
        accountCopRepo.saveAll(cuentas);

        System.out.println("✅ Saldos iniciales del día actualizados");
    }
}
