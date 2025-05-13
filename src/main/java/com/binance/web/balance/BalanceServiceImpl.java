package com.binance.web.balance;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.Balance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.BalanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SaleP2PRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.balance.PurchaseRate.PurchaseRateService;

@Service
public class BalanceServiceImpl implements BalanceService {

	@Autowired
	private BalanceRepository balanceRepository;

	@Autowired
	private SaleP2PRepository saleP2PRepository;

	@Autowired
	private SellDollarsRepository sellDollarsRepository;

	@Autowired
	private BuyDollarsRepository buyDollarsRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private PurchaseRateService purchaseRateService;

	@Override
	public List<BalanceDTO> showBalances() {
		List<Balance> listBalances = balanceRepository.findAll();
		List<BalanceDTO> listBalanceDTOs = listBalances.stream().map(this::convertBalanceToDto)
				.collect(Collectors.toList());

		return listBalanceDTOs;
	}

	@Override
	public void createBalance(Date date) {
		Double usdt = getDollarsBalance(date);
		Double deuda = getSupplierDebt();
		Double gastos = 0.0;
		Double pesos = calculatePesos(date, usdt);
		Double Saldo = 0.0;
		Balance balance = new Balance();
		balance.setDate(date);
		balance.setDeuda(deuda);
		balance.setGastos(gastos);
		balance.setPesos(pesos);
		balance.setUsdt(usdt);
		balance.setSaldo(Saldo);
		balanceRepository.save(balance);
		purchaseRateService.createPurchaseRate(balance);
	}

	private Double getDollarsBalance(Date date) {
		Double dayBuyedDollars = getDayBuyedDollars(date, true);
		Double daySalesDollars = getDaySalesDollars(date);
		Balance previusBalance = balanceRepository.findTopByOrderByIdDesc().orElse(null);

		Double previusBalanceUsdt = previusBalance != null ? previusBalance.getUsdt() : 0.0;
		Double dolares = previusBalanceUsdt + (dayBuyedDollars - daySalesDollars);
		return dolares;
	}

	private Double getDayBuyedDollars(Date date, Boolean inDollars) {
		Double dollarsBuyed = 0.0;
		Double dollarsBuyedPesos = 0.0;
		List<BuyDollars> buyDollarsDay = buyDollarsRepository.findByDateWithoutTime(date);
		System.out.println("La fehca es:" + date + "Y la lista de compras es: " + buyDollarsDay);
		for (BuyDollars buyDollar : buyDollarsDay) {
			dollarsBuyed += buyDollar.getDollars();
			dollarsBuyedPesos += buyDollar.getPesos();
		}
		return inDollars ? dollarsBuyed : dollarsBuyedPesos;
	}

	private Double getDaySalesDollars(Date date) {
		Double dollarsSales = 0.0;
		List<SaleP2P> daySalesDollars = saleP2PRepository.findByDateWithoutTime(date);
		List<SellDollars> daySellDollars = sellDollarsRepository.findByDateWithoutTime(date);
		for (SaleP2P saleDollar : daySalesDollars) {
			dollarsSales += saleDollar.getDollarsUs();
		}
		for (SellDollars sellDollar : daySellDollars) {
			dollarsSales += sellDollar.getDollars();
		}
		return dollarsSales;
	}

	private Double getSupplierDebt() {
		Supplier supplier = supplierRepository.findByName("Deuda");
		return supplier.getBalance();
	}

	private Double calculatePesos(Date date, Double usdt) {
		Double purchaseRateDay = calculatePurchaseRate(date);
		return purchaseRateDay * usdt;
	}

	private Double calculatePurchaseRate(Date date) {
		Double dayBuyedDollars = getDayBuyedDollars(date, true);
		Double dayBuyedDollarsPesos = getDayBuyedDollars(date, false);
		Balance previusBalance = balanceRepository.findTopByOrderByIdDesc().orElse(null);
		Double purchaseRateDay = createPurchaseRate(dayBuyedDollars, dayBuyedDollarsPesos, previusBalance, date);
		return purchaseRateDay;
	}

	private Double createPurchaseRate(Double dayBuyedDollars, Double dayBuyedDollarsPesos, Balance previusBalance,
			Date date) {
		Double purchaseRateDay = 0.0;
		if (previusBalance != null) {
			purchaseRateDay = (dayBuyedDollarsPesos + previusBalance.getPesos())
					/ (dayBuyedDollars + previusBalance.getUsdt());
		} else {
			purchaseRateDay = dayBuyedDollarsPesos / dayBuyedDollars;
		}
		return purchaseRateDay;
	}

	private BalanceDTO convertBalanceToDto(Balance balance) {
		BalanceDTO dto = new BalanceDTO();
		dto.setId(balance.getId());
		dto.setDate(balance.getDate());
		dto.setSaldo(balance.getSaldo());
		return dto;
	}
}