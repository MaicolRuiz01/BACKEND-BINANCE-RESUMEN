package com.binance.web.OrderP2P;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.AccountBinance.AccountBinance;
import com.binance.web.AccountBinance.AccountBinanceRepository;
import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.BinanceAPI.OrderMapperServiceImpl;
import com.binance.web.SaleP2P.SaleP2P;
import com.binance.web.SaleP2P.SaleP2PRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Service
public class OrderP2PServiceImpl implements OrderP2PService {

	@Autowired
	private SaleP2PRepository saleP2PRepository;
	
	@Autowired
	private BinanceService binanceService;

	@Autowired
	private AccountBinanceRepository accountBinanceRepository;
	

	@Override
	public List<OrderP2PDto> showOrderP2PToday(String account) {
		List<OrderP2PDto> ordenesP2P;
		Date date = getTodayDate();
		ordenesP2P = getOrderP2P(account);
		ordenesP2P = getOrderP2pCompleted(ordenesP2P);
		ordenesP2P = getOrderP2pByDate(ordenesP2P, date);
		ordenesP2P = assignAccountIfExists(ordenesP2P, account);
		return ordenesP2P;
	}
	
	private List<OrderP2PDto> assignAccountIfExists(List<OrderP2PDto> ordenes, String accountName) {
	    // Buscar la cuenta de Binance por nombre
	    AccountBinance accountBinance = accountBinanceRepository.findByName(accountName);
	    
	    if (accountBinance == null) {
	        // Si no se encuentra la cuenta, devolver las órdenes sin cambios
	        return ordenes;
	    }

	    // 1. Obtener todas las ventas asociadas a la cuenta de Binance
	    List<SaleP2P> salesByAccount = saleP2PRepository.findByBinanceAccount(accountBinance);

	    // Convertir la lista de resultados en un Mapa para búsqueda O(1)
	    Map<String, SaleP2P> saleMap = salesByAccount.stream()
	        .collect(Collectors.toMap(sale -> String.valueOf(sale.getNumberOrder()), sale -> sale));

	    // Recorrer las órdenes y asignar la cuenta si existe en el mapa
	    ordenes.forEach(order -> {
	        SaleP2P sale = saleMap.get(order.getOrderNumber());
	        if (sale != null) {
	            order.setNameAccount(sale.getNameAccount()); // Asignar el nombre de la cuenta
	            order.setAccountAmount(sale.getPesosCop()); // Asignar el monto de la cuenta asociada
	            // Agrega más modificaciones aquí si necesitas otros valores de sale
	        }
	    });

	    return ordenes;
	}
	
	private List<OrderP2PDto> getOrderP2P(String account) {
		// Llamamos al servicio que obtiene el JSON de Binance
		String jsonResponse = binanceService.getP2POrderLatest(account);

		// Convertimos la respuesta JSON en un objeto de Gson
		JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

		// Si hay un error en la respuesta, devolvemos una lista vacía
		if (jsonObject.has("error")) {
			System.err.println("Error al obtener órdenes: " + jsonObject.get("error").getAsString());
			return List.of(); // Devuelve una lista vacía en vez de null
		}

		// Extraemos el array "data" del JSON
		JsonArray dataArray = jsonObject.getAsJsonArray("data");

		// Convertimos el JsonArray en una lista de DTOs usando IntStream.range()
		return IntStream.range(0, dataArray.size())
				.mapToObj(i -> OrderMapperServiceImpl.convertToDTO(dataArray.get(i).getAsJsonObject()))
				.collect(Collectors.toList());
	}

	private List<OrderP2PDto> getOrderP2pCompleted(List<OrderP2PDto> ordenes) {
		return ordenes.stream().filter(order -> "COMPLETED".equalsIgnoreCase(order.getOrderStatus()))
				.collect(Collectors.toList());
	}

	private List<OrderP2PDto> getOrderP2pByDate(List<OrderP2PDto> ordenes, Date fecha) {
		return ordenes.stream().filter(order -> isSameDay(order.getCreateTime(), fecha)).collect(Collectors.toList());
	}

	private boolean isSameDay(Date date1, Date date2) {
		if (date1 == null || date2 == null)
			return false;

		return date1.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
				.equals(date2.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate());
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
