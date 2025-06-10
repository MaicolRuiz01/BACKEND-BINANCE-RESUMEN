package com.binance.web.balance;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.Balance;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Repository.BalanceRepository;
import com.binance.web.Repository.SaleP2PRepository;

@Service
public class BalanceServiceImpl implements BalanceService {

	@Autowired
	private BalanceRepository balanceRepository;

	@Autowired
	private SaleP2PRepository saleP2PRepository;

	@Override
	public List<BalanceDTO> showBalances() {
		List<Balance> listBalances = balanceRepository.findAll();
		List<BalanceDTO> listBalanceDTOs = listBalances.stream().map(this::convertBalanceToDto)
				.collect(Collectors.toList());

		return listBalanceDTOs;
	}

	@Override
	public void createBalance(LocalDate date) {
		Double saldo = getSaldoBalance(date);
		Balance balance = new Balance();

		balance.setDate(date.atStartOfDay());
		balance.setSaldo(saldo);
		balanceRepository.save(balance);
	}

	@Override
	public BalanceDTO showLiveBalanceToday() {
		LocalDate date = LocalDate.now();
		Double saldo = getSaldoBalance(date);
		BalanceDTO dto = new BalanceDTO();
		dto.setDate(date);
		dto.setSaldo(saldo);
		return dto;
	}

	private Double getSaldoBalance(LocalDate date) {
		List<SaleP2P> salesP2P = saleP2PRepository.findByDateWithoutTime(date);
		Double saldo = 0.0;

		for (SaleP2P sale : salesP2P) {
			saldo += sale.getUtilidad();
		}
		return saldo;
	}

	private BalanceDTO convertBalanceToDto(Balance balance) {
		BalanceDTO dto = new BalanceDTO();
		dto.setId(balance.getId());
		dto.setDate(balance.getDate().toLocalDate());
		dto.setSaldo(balance.getSaldo());
		return dto;
	}
}