package com.binance.web.serviceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SaleP2pAccountCop;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.SaleP2PRepository;
import com.binance.web.Repository.SaleP2pAccountCopRepository;
import com.binance.web.service.AccountCopService;
import com.binance.web.util.CupoDiarioRules;

@Service
public class AccountCopServiceImpl implements AccountCopService {

	private final AccountCopRepository AccountCopRepository;
	private final SaleP2pAccountCopRepository saleP2pAccountCopRepository;
	private final SaleP2PRepository saleP2PRepository;
	private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

	public AccountCopServiceImpl(AccountCopRepository AccountCopRepository, SaleP2PRepository saleP2PRepository, SaleP2pAccountCopRepository saleP2pAccountCopRepository) {
	    this.AccountCopRepository = AccountCopRepository;
	    this.saleP2PRepository = saleP2PRepository;
	    this.saleP2pAccountCopRepository = saleP2pAccountCopRepository;
	}

	@Override
	@Transactional
	public List<AccountCop> findAllAccountCop() {
		List<AccountCop> cuentasCop = AccountCopRepository.findAllWithBrebeKeys();
		LocalDate hoy = LocalDate.now(ZONE_BOGOTA);
		boolean alguienActualizado = false;
		for (AccountCop acc : cuentasCop) {
			if (acc.getBankType() == null) continue;
			boolean diaDistinto = acc.getCupoFecha() == null || !hoy.equals(acc.getCupoFecha());
			if (diaDistinto || acc.getCupoCajeroDisponibleHoy() == null || acc.getCupoCorresponsalDisponibleHoy() == null) {
				double cajero       = CupoDiarioRules.maxCajeroPorBanco(acc.getBankType());
				double corresponsal = CupoDiarioRules.maxCorresponsalPorBanco(acc.getBankType());
				acc.setCupoFecha(hoy);
				acc.setCupoCajeroDisponibleHoy(cajero);
				acc.setCupoCorresponsalDisponibleHoy(corresponsal);
				acc.setCupoDiarioMax(cajero + corresponsal);
				acc.setCupoDisponibleHoy(cajero + corresponsal);
				AccountCopRepository.save(acc);
				alguienActualizado = true;
			}
		}
		return cuentasCop;
	}

	@Override
	public AccountCop findByIdAccountCop(Integer id) {
		return AccountCopRepository.findById(id).orElse(null);
	}

	@Override
	public List<AccountCopRepository.SaldoView> findAllSaldos() {
		return AccountCopRepository.findAllSaldos();
	}


	@Override
    public void saveAccountCop(AccountCop accountCop) {
        if (accountCop.getName() == null || accountCop.getBalance() == null) {
            throw new IllegalArgumentException("El nombre de la cuenta y el saldo no pueden ser nulos.");
        }
        if (accountCop.getBankType() == null) {
            throw new IllegalArgumentException("bankType es obligatorio.");
        }

        // ✅ validar número de cuenta duplicado
        String num = accountCop.getNumeroCuenta();
        if (num != null && !num.isBlank()) {
            if (AccountCopRepository.existsByNumeroCuenta(num.trim())) {
                throw new IllegalArgumentException(
                    "Ya existe una cuenta COP con el número: " + num.trim());
            }
            accountCop.setNumeroCuenta(num.trim());
        }

        // ✅ saldo inicial del día
        accountCop.setSaldoInicialDelDia(accountCop.getBalance());

        // ✅ inicializar cupos al crear
        double cajero       = CupoDiarioRules.maxCajeroPorBanco(accountCop.getBankType());
        double corresponsal = CupoDiarioRules.maxCorresponsalPorBanco(accountCop.getBankType());
        accountCop.setCupoCajeroDisponibleHoy(cajero);
        accountCop.setCupoCorresponsalDisponibleHoy(corresponsal);
        accountCop.setCupoDiarioMax(cajero + corresponsal);
        accountCop.setCupoDisponibleHoy(cajero + corresponsal);
        accountCop.setCupoFecha(LocalDate.now(ZONE_BOGOTA));

        AccountCopRepository.save(accountCop);
    }

	@Override
	public void updateAccountCop(Integer id, AccountCop accountCop) {
	    AccountCop existing = AccountCopRepository.findById(id).orElse(null);
	    if (existing == null) {
	        throw new IllegalArgumentException("La cuenta con el ID " + id + " no existe.");
	    }

	    // ✅ validar número de cuenta duplicado al editar
	    String num = accountCop.getNumeroCuenta();
	    if (num != null && !num.isBlank()) {
	        if (AccountCopRepository.existsByNumeroCuentaAndIdNot(num.trim(), id)) {
	            throw new IllegalArgumentException(
	                "Ya existe otra cuenta COP con el número: " + num.trim());
	        }
	        existing.setNumeroCuenta(num.trim());
	    } else {
	        existing.setNumeroCuenta(null);
	    }

	    existing.setName(accountCop.getName());
	    existing.setBalance(accountCop.getBalance());
	    existing.setCedula(accountCop.getCedula());

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

	@Override
	public void saveAccountCopSafe(AccountCop accountCop) {
	    AccountCop existing = AccountCopRepository.findById(accountCop.getId()).orElse(null);
	    if (existing == null) {
	        throw new RuntimeException("No existe AccountCop id " + accountCop.getId());
	    }

	    // ✅ SOLO actualiza campos que cambian en movimientos
	    existing.setBalance(accountCop.getBalance());
	    existing.setCupoDisponibleHoy(accountCop.getCupoDisponibleHoy());

	    AccountCopRepository.save(existing);
	}
	@Override
	@Transactional
	public String reconcileAccountCop(Integer accId) {
	    ZoneId zone = ZoneId.of("America/Bogota");
	    LocalDate today = LocalDate.now(zone);
	    LocalDateTime start = today.atStartOfDay();
	    LocalDateTime end = start.plusDays(1);

	    AccountCop acc = AccountCopRepository.findById(accId)
	        .orElseThrow(() -> new RuntimeException("No existe cuenta " + accId));

	    // BASE: si no tienes base, usa 0 (tu caso)
	    double baseBalance = 0.0;

	    double totalAll = saleP2pAccountCopRepository.sumAllByAccount(accId);
	    double totalToday = saleP2pAccountCopRepository.sumByAccountBetween(accId, start, end);

	    if (acc.getCupoDiarioMax() == null && acc.getBankType() != null) {
	        double c = CupoDiarioRules.maxCajeroPorBanco(acc.getBankType());
	        double r = CupoDiarioRules.maxCorresponsalPorBanco(acc.getBankType());
	        acc.setCupoDiarioMax(c + r);
	    }
	    double cupoMax = acc.getCupoDiarioMax() != null ? acc.getCupoDiarioMax() : 0.0;

	    double newBalance = baseBalance + totalAll;
	    double newCupoHoy = cupoMax - totalToday;

	    // por seguridad
	    if (newCupoHoy < 0) newCupoHoy = 0;

	    acc.setBalance(newBalance);
	    acc.setCupoDisponibleHoy(newCupoHoy);
	    acc.setCupoFecha(today);

	    AccountCopRepository.save(acc);

	    return "OK balance=" + newBalance + " cupoDisponibleHoy=" + newCupoHoy;
	}


}
