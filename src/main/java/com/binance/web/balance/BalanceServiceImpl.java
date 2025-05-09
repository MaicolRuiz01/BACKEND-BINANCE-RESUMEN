package com.binance.web.balance;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SaleP2PRepository;
import com.binance.web.Repository.SupplierRepository;

@Service
public class BalanceServiceImpl {
	
	@Autowired
	private BalanceRepository balanceRepository;
	
	@Autowired
	private SaleP2PRepository saleP2PRepository;
	
	@Autowired
	private BuyDollarsRepository buyDollarsRepository;
	
	@Autowired
	private SupplierRepository supplierRepository;

	public void  getBalanceRealTime(Date date) {
		Double usdt = getDollarsBalance(date);
		Double deuda = getSupplierDebt();
	}
	
	private Double getDollarsBalance(Date date) {
		Double dayBuyedDollars = getDayBuyedDollars(date);
		Double daySalesDollars = getDaySalesDollars(date);
		Balance previusBalance = balanceRepository.findTopByOrderByIdDesc();
		Double dolares;
		dolares = previusBalance.getUsdt() + (dayBuyedDollars - daySalesDollars);
		return dolares;
	}
	
	private Double getDayBuyedDollars(Date date) {
		Double dollarsBuyed = 0.0;
		List<BuyDollars> buyDollarsDay = buyDollarsRepository.findByDateWithoutTime(date);
		for (BuyDollars buyDollar: buyDollarsDay) {
			dollarsBuyed += buyDollar.getDollars();
		}
		return dollarsBuyed;
	}
	
	private Double getDaySalesDollars(Date date) {
		Double dollarsSales = 0.0;
		List<SaleP2P> daySalesDollars = saleP2PRepository.findByDateWithoutTime(date);
		for (SaleP2P SaleDollar: daySalesDollars) {
			dollarsSales += SaleDollar.getDollarsUs();
		}
		return dollarsSales;
	}
	
	private Double getSupplierDebt() {
		Supplier supplier = supplierRepository.findByName("Deuda");
		return supplier.getBalance();
	}
}
