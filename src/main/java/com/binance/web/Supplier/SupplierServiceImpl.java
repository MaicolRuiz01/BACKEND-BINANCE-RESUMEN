package com.binance.web.Supplier;

import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.AccountBinance.AccountBinance;
import com.binance.web.AccountBinance.AccountBinanceRepository;
import com.binance.web.OrderP2P.OrderP2PDto;
import com.binance.web.OrderP2P.OrderP2PService;

@Service
public class SupplierServiceImpl implements SupplierService {
	
	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private AccountBinanceRepository accountBinanceRepository;
	
	@Autowired
	private OrderP2PService orderP2PService;
	
	@Override
	public void saveSupplier(Supplier supplier) {
		supplierRepository.save(supplier);
	}

	@Override
	public void subtractSupplierDebt(Double pesosCop, String taxType, Date date) {
		Supplier supplier = supplierRepository.findByName("Deuda");
//		if(taxType.contentEquals("2x")) {
//			pesosCop = pesosCop * 0.998;
//		}
		
		if(taxType.contentEquals("4x")) {
			pesosCop = pesosCop * 0.996;
		}
		
		Double balance = supplier.getBalance() - pesosCop;
		supplier.setBalance(balance);
		supplier.setLastPaymentDate(date);
		saveSupplier(supplier);
	}
	
	@Override
	public Double subtractAllSalesFromSupplier() {
		Supplier supplier = supplierRepository.findByName("Deuda");
		Double ventasNoAsignadas = 0.0;
		Date endDate = getTodayDate();
		Date startDate = Date.from(
			    supplier.getLastPaymentDate()
			        .toInstant()
			        .atZone(ZoneId.systemDefault())
			        .toLocalDate()
			        .atStartOfDay(ZoneId.systemDefault())
			        .toInstant()
			);
		List<AccountBinance> binanceAccout = accountBinanceRepository.findAll();
		for (AccountBinance binAccount : binanceAccout) {
			List<OrderP2PDto> ordenes = orderP2PService.showOrderP2PByDateRange(binAccount.getName(), startDate, endDate);
			ordenes = deleteOrdersWithAssignedAccount(ordenes);
			for (OrderP2PDto orden : ordenes) {
				ventasNoAsignadas += orden.getTotalPrice() * 0.996;
			}
		}
		return supplier.getBalance() - ventasNoAsignadas;
	}
	
	private List<OrderP2PDto> deleteOrdersWithAssignedAccount(List<OrderP2PDto> ordenes) {
		  return ordenes.stream()
                  .filter(orden -> orden.getNameAccount() == null)
                  .collect(Collectors.toList());
	}
	
	 private Date getTodayDate() {
	        Calendar calendar = Calendar.getInstance();
	        calendar.set(Calendar.HOUR_OF_DAY, 0);
	        calendar.set(Calendar.MINUTE, 0);
	        calendar.set(Calendar.SECOND, 0);
	        calendar.set(Calendar.MILLISECOND, 0);
	        return calendar.getTime();
	    }
}
