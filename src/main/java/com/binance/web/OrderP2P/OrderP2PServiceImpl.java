package com.binance.web.OrderP2P;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.BinanceAPI.OrderMapperServiceImpl;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.SaleP2PRepository;

@Slf4j
@Service
public class OrderP2PServiceImpl implements OrderP2PService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired private SaleP2PRepository saleP2PRepository;
    @Autowired private BinanceService binanceService;
    @Autowired private AccountBinanceRepository accountBinanceRepository;

    @Override
    public List<OrderP2PDto> showOrderP2PToday(String account, LocalDate today) {
        List<OrderP2PDto> orders = getOrderP2P(account);
        orders = getOrderP2pCompleted(orders);
        orders = getOrderP2pByDate(orders, today);
        return orders;
    }

    @Override
    public List<OrderP2PDto> showOrderP2PByDateRange(String account, LocalDate fechaInicio, LocalDate fechaFin) {
        List<OrderP2PDto> orders = getAllOrderP2P(account, fechaInicio, fechaFin);
        orders = filterNewSales(orders);
        orders = getOrderP2pCompleted(orders);
        return orders;
    }

    @Override
    public List<OrderP2PDto> showAllOrderP2(String account) {
        List<OrderP2PDto> orders = getOrderP2P(account);
        return getOrderP2pCompleted(orders);
    }

    /** Usa query de IDs para evitar cargar entidades completas. */
    private List<OrderP2PDto> filterNewSales(List<OrderP2PDto> orders) {
        Set<String> existingNumbers = saleP2PRepository.findAllOrderNumbers();
        return orders.stream()
                .filter(o -> !existingNumbers.contains(o.getOrderNumber()))
                .collect(Collectors.toList());
    }

    private List<OrderP2PDto> getOrderP2P(String account) {
        return parseOrders(binanceService.getP2POrderLatest(account));
    }

    private List<OrderP2PDto> getAllOrderP2P(String account, LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of("America/Bogota");
        long start  = from.atStartOfDay(zone).toInstant().toEpochMilli();
        long end    = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return parseOrders(binanceService.getP2POrdersInRange(account, start, end, null));
    }

    private List<OrderP2PDto> parseOrders(String jsonResponse) {
        try {
            JsonNode root = mapper.readTree(jsonResponse);
            if (root.has("error")) {
                log.error("Error al obtener órdenes P2P: {}", root.get("error").asText());
                return List.of();
            }
            JsonNode data = root.path("data");
            if (!data.isArray()) return List.of();
            return StreamSupport.stream(data.spliterator(), false)
                    .map(OrderMapperServiceImpl::convertToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error parseando respuesta P2P: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<OrderP2PDto> getOrderP2pCompleted(List<OrderP2PDto> orders) {
        return orders.stream()
                .filter(o -> "COMPLETED".equalsIgnoreCase(o.getOrderStatus()))
                .collect(Collectors.toList());
    }

    private List<OrderP2PDto> getOrderP2pByDate(List<OrderP2PDto> orders, LocalDate fecha) {
        return orders.stream()
                .filter(o -> o.getCreateTime() != null && o.getCreateTime().toLocalDate().equals(fecha))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderP2PDto> ultimasOrdenes(int cantidad) {
        return binanceService.getAllAccountNames().stream()
                .flatMap(cuenta -> {
                    try {
                        List<OrderP2PDto> orders = getOrderP2pCompleted(getOrderP2P(cuenta));
                        orders.forEach(o -> o.setNameAccount(cuenta));
                        return orders.stream();
                    } catch (Exception e) {
                        log.warn("Error obteniendo ordenes de {}: {}", cuenta, e.getMessage());
                        return Stream.empty();
                    }
                })
                .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
                .limit(cantidad)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderP2PDto> getUltimasOrdenesTodas(int cantidad) {
        return binanceService.getAllAccountNames().stream()
                .flatMap(cuenta -> {
                    try {
                        List<OrderP2PDto> orders = getOrderP2P(cuenta);
                        orders.forEach(o -> o.setNameAccount(cuenta));
                        return orders.stream();
                    } catch (Exception e) {
                        log.warn("Error obteniendo ordenes de {}: {}", cuenta, e.getMessage());
                        return Stream.empty();
                    }
                })
                .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
                .limit(cantidad)
                .collect(Collectors.toList());
    }
}
