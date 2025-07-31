package com.binance.web.SaleP2P;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.AccountCop.AccountCopService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.PurchaseRate;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SaleP2pAccountCop;
import com.binance.web.OrderP2P.OrderP2PDto;
import com.binance.web.OrderP2P.OrderP2PService;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.PurchaseRateRepository;
import com.binance.web.Repository.SaleP2PRepository;

@Service
public class SaleP2PServiceImpl implements SaleP2PService {

	@Autowired
	private SaleP2PRepository saleP2PRepository;

	@Autowired
	private PurchaseRateRepository purchaseRateRepository;

	@Autowired
	private AccountCopService accountCopService;

	@Autowired
	private OrderP2PService orderP2PService;

	@Autowired
	private AccountBinanceRepository accountBinanceRepository;

	@Autowired
	private AccountBinanceService accountBinanceService;

	@Override
	public List<SaleP2PDto> findAllSaleP2P() {
		return saleP2PRepository.findAll()
				.stream()
				.map(this::convertToDto)
				.collect(Collectors.toList());
}

	@Override
	public SaleP2P findByIdSaleP2P(Integer id) {
		SaleP2P saleP2P = saleP2PRepository.findById(id).get();
		return saleP2P;
	}

	@Override
	public void saveSaleP2P(SaleP2P saleP2P) {
		saleP2PRepository.save(saleP2P);
	}

	@Override
	public void updateSaleP2P(Integer id, SaleP2P sale) {
		SaleP2P saleP2P = saleP2PRepository.findById(id).orElse(null);
		saleP2PRepository.save(saleP2P);
	}

	@Override
	public void deleteSaleP2P(Integer id) {
		saleP2PRepository.deleteById(id);
	}
	
	//este metodo ya no se impementa en el fronted mas sin embargo se deja por si acaso
	@Override
	public List<SaleP2P> obtenerVentasEntreFechas(LocalDateTime inicio, LocalDateTime fin) {
		return saleP2PRepository.findByDateBetween(inicio, fin);
	}

	@Override
	public List<SaleP2PDto> getLastSaleP2pToday(String account) {
		LocalDate today = LocalDate.now();
		createSaleP2p(account, today);
		return convertToDtoList(today, account);

	}

	private void createSaleP2p(String account, LocalDate today) {
		List<OrderP2PDto> ordenesToday = orderP2PService.showOrderP2PToday(account, today);
		ordenesToday = filterNewOrdensToday(ordenesToday, today);
		orderP2pDtoToSaleP2p(ordenesToday, account);
	}

	private List<OrderP2PDto> filterNewOrdensToday(List<OrderP2PDto> ordenesToday, LocalDate today) {
		List<String> convertedOrderNumbers = saleP2PRepository.findByDateWithoutTime(today).stream()
				.map(SaleP2P::getNumberOrder).collect(Collectors.toList());
		ordenesToday = ordenesToday.stream().filter(order -> !convertedOrderNumbers.contains(order.getOrderNumber()))
				.collect(Collectors.toList());
		return ordenesToday;
	}

	//este metodo comvierte la inforamcion que traigo atravez de la api de binance y lo paso por el dto
	private void orderP2pDtoToSaleP2p(List<OrderP2PDto> ordenesToday, String account) {
		for (OrderP2PDto orderP2p : ordenesToday) {
			SaleP2P sale = new SaleP2P();
			sale.setNumberOrder(orderP2p.getOrderNumber());
			sale.setDate(orderP2p.getCreateTime());
			sale.setCommission(orderP2p.getCommission());
			sale.setPesosCop(orderP2p.getTotalPrice());
			sale.setDollarsUs(orderP2p.getAmount());
			sale = assignAccountBinance(sale, account);
			sale.setUtilidad(generateUtilidad(sale));
			saveSaleP2P(sale);
		}
	}

	private Double generateUtilidad(SaleP2P sale) {
		PurchaseRate lastRate = purchaseRateRepository.findTopByOrderByDateDesc();
		Double pesosUsdtVendidos = sale.getPesosCop();
		Double usdtVendidos = sale.getDollarsUs() + sale.getCommission();
		Double utilidad = pesosUsdtVendidos - (usdtVendidos * lastRate.getRate()) - (sale.getPesosCop() * 0.004);
		return utilidad;
	}

	private Double generateTax(SaleP2P sale) {
		Double impuesto = 0.0;
		List<SaleP2pAccountCop> accountCop = sale.getAccountCopsDetails();
		for (SaleP2pAccountCop account : accountCop) {
			if (account.getAccountCop() == null) {
				impuesto += account.getAmount() * 0.004;
			}
		}
		return impuesto;
	}

	public void saveUtilitydefinitive(List<SaleP2P> rangeSales, Double averageRate) {
		Double pesosUsdtVendidos = 0.0;
		Double usdtVendidos = 0.0;
		Double utilidad = 0.0;
		for (SaleP2P sale : rangeSales) {
			pesosUsdtVendidos = sale.getPesosCop();
			usdtVendidos = sale.getDollarsUs() + sale.getCommission();
			utilidad = pesosUsdtVendidos - (usdtVendidos * averageRate);
			utilidad = utilidad - generateTax(sale);
			sale.setUtilidad(utilidad);
			saleP2PRepository.save(sale);
		}
	}

	@Override
	public String processAssignAccountCop(Integer saleId, List<AssignAccountDto> accounts) {
		SaleP2P sale = saleP2PRepository.findById(saleId).orElse(null);
		if (sale == null)
			return "No se realizo la asignacion de cuenta, No se encontro la venta con id" + saleId;
		if (!accounts.isEmpty()) {
			sale = assignAccountCop(accounts, sale);
		}
		accountBinanceService.subtractBalance(sale.getBinanceAccount().getName(), sale.getDollarsUs());
		sale.setUtilidad(sale.getUtilidad() + generateTax(sale));
		saleP2PRepository.save(sale);
		return "Asignacion de cuenta realizada con exito";
	}

	private SaleP2P assignAccountCop(List<AssignAccountDto> accounts, SaleP2P sale) {
	    // Aseguramos que la lista ya está inicializada
	    if (sale.getAccountCopsDetails() == null) {
	        sale.setAccountCopsDetails(new ArrayList<>());
	    } else {
	        // Eliminar correctamente las relaciones inversas para evitar errores
	        for (SaleP2pAccountCop acc : sale.getAccountCopsDetails()) {
	            acc.setSaleP2p(null);
	        }
	        sale.getAccountCopsDetails().clear();
	    }

	    for (AssignAccountDto account : accounts) {
	        SaleP2pAccountCop assignAccount = new SaleP2pAccountCop();
	        assignAccount.setSaleP2p(sale);
	        assignAccount.setAmount(account.getAmount());
	        assignAccount.setNameAccount(account.getNameAccount());

	        if (account.getAccountCop() != null) {
	            AccountCop accountCop = accountCopService.findByIdAccountCop(account.getAccountCop());
	            assignAccount.setAccountCop(accountCop);
	            accountCop.setBalance(accountCop.getBalance() + account.getAmount());
	            accountCopService.saveAccountCop(accountCop);
	        }

	        sale.getAccountCopsDetails().add(assignAccount);
	    }

	    return sale;
	}


	private SaleP2P assignAccountBinance(SaleP2P sale, String name) {
		AccountBinance accountBinance = accountBinanceRepository.findByName(name);
		sale.setBinanceAccount(accountBinance);
		return sale;
	}

	private List<SaleP2PDto> convertToDtoList(LocalDate today, String name) {
		AccountBinance accountBinance = accountBinanceRepository.findByName(name);
		List<SaleP2P> sales = saleP2PRepository.findByDateAndBinanceAccount(today, accountBinance.getId());
		return sales.stream().map(this::convertToDto).collect(Collectors.toList());
	}

	private SaleP2PDto convertToDto(SaleP2P sale) {
    SaleP2PDto dto = new SaleP2PDto();
    dto.setId(sale.getId());
    dto.setNumberOrder(sale.getNumberOrder());
    dto.setDate(sale.getDate());
    dto.setCommission(sale.getCommission());
    dto.setPesosCop(sale.getPesosCop());
    dto.setDollarsUs(sale.getDollarsUs());
    dto.setNameAccountBinance(getBinanceAccountName(sale));
    return dto;
}
	
	private String getBinanceAccountName(SaleP2P sale) {
		return sale.getBinanceAccount() != null ? sale.getBinanceAccount().getName() : null;
	}
	
	@Override
	public List<SaleP2P> obtenerVentasPorFecha(LocalDate fecha) {
	    LocalDateTime start = fecha.atStartOfDay();
	    LocalDateTime end = start.plusDays(1);
	    return saleP2PRepository.findByDateBetween(start, end);
	}
	
	@Override
	public Double obtenerComisionesPorFecha(LocalDate fecha) {
	    LocalDateTime start = fecha.atStartOfDay();
	    LocalDateTime end = start.plusDays(1);
	    return saleP2PRepository.findByDateBetween(start, end).stream()
	        .mapToDouble(SaleP2P::getCommission)
	        .sum();
	}

	

}
