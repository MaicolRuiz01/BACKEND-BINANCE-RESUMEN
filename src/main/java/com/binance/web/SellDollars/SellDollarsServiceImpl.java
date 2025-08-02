package com.binance.web.SellDollars;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.binance.web.SaleP2P.AssignAccountDto;
import com.binance.web.model.Transaction;
import com.binance.web.AccountCop.AccountCopService;
import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.BinanceAPI.PaymentController;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.PurchaseRate;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.SellDollarsAccountCop;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.PurchaseRateRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.Entity.Supplier;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SellDollarsServiceImpl implements SellDollarsService{
	
	@Autowired
    private SellDollarsRepository sellDollarsRepository;
	
	@Autowired
    private AccountBinanceRepository accountBinanceRepository;
	@Autowired
	private SupplierRepository supplierRepository;
	@Autowired
	private BinanceService binanceService;
	@Autowired
	private AccountCopService accountCopService; // para obtener y actualizar balances
	@Autowired
	private PaymentController paymentController;
	@Autowired
	private PurchaseRateRepository purchaseRateRepository;
	@Autowired
	private ClienteRepository clienteRepository;

	@Override
	@Transactional
	public SellDollars createSellDollars(SellDollarsDto dto) {
	    // 1. Cargar cuenta Binance
	    AccountBinance accountBinance = accountBinanceRepository.findById(dto.getAccountBinanceId())
	        .orElseThrow(() -> new RuntimeException("AccountBinance not found"));

	    // 2. Crear objeto venta
	    SellDollars sale = new SellDollars();
	    sale.setIdWithdrawals(dto.getIdWithdrawals());
	    sale.setTasa(dto.getTasa());
	    sale.setDollars(dto.getDollars());
	    sale.setDate(dto.getDate());
	    sale.setNameAccount(dto.getNameAccount());
	    sale.setAccountBinance(accountBinance);
	    sale.setPesos(dto.getPesos());
	    sale.setAsignado(true);
	    sale.setUtilidad(utilidad(sale));

	    // 3. Ajustar saldo de la cuenta Binance
	    double currentBinanceBalance = accountBinance.getBalance() != null ? accountBinance.getBalance() : 0.0;
	    accountBinance.setBalance(currentBinanceBalance - dto.getDollars());

	    // ⚡ Caso 1: Si hay cliente, se descuenta del saldo del cliente
	    if (dto.getClienteId() != null) {
	        Cliente cliente = clienteRepository.findById(dto.getClienteId())
	            .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
	        sale.setCliente(cliente);
	        sale.setSupplier(null);  // 🔥 Importante

	        double saldoActual = cliente.getSaldo() != null ? cliente.getSaldo() : 0.0;
	        cliente.setSaldo(saldoActual - dto.getPesos());
	        clienteRepository.save(cliente);
	    } 
	    // 🏦 Caso 2: Si no hay cliente, flujo normal: cuentas COP y proveedor
	    else {
	        Supplier supplier = supplierRepository.findById(dto.getSupplier())
	            .orElseThrow(() -> new RuntimeException("Supplier no encontrado"));
	        sale.setSupplier(supplier);
	        sale.setCliente(null); // 🔥 Importante

	        double totalAsignadoACuentas = 0.0;
	        List<SellDollarsAccountCop> detalles = new ArrayList<>();

	        if (dto.getAccounts() != null && !dto.getAccounts().isEmpty()) {
	            for (AssignAccountDto assignDto : dto.getAccounts()) {
	                AccountCop acc = accountCopService.findByIdAccountCop(assignDto.getAccountCop());
	                double currentCopBalance = acc.getBalance() != null ? acc.getBalance() : 0.0;
	                acc.setBalance(currentCopBalance + assignDto.getAmount());
	                accountCopService.saveAccountCop(acc);

	                SellDollarsAccountCop detalle = new SellDollarsAccountCop();
	                detalle.setSellDollars(sale);
	                detalle.setAccountCop(acc);
	                detalle.setAmount(assignDto.getAmount());
	                detalle.setNameAccount(assignDto.getNameAccount());
	                detalles.add(detalle);

	                totalAsignadoACuentas += assignDto.getAmount();
	            }
	        }

	        double montoEnPesos = dto.getPesos() != null
	            ? dto.getPesos()
	            : dto.getDollars() * dto.getTasa();
	        double restanteParaProveedor = montoEnPesos - totalAsignadoACuentas;

	        if (restanteParaProveedor > 0.0) {
	            double currentSupplierBalance = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
	            supplier.setBalance(currentSupplierBalance - restanteParaProveedor);
	        }

	        sale.setSellDollarsAccounts(detalles);
	        supplierRepository.save(supplier);
	    }

	    accountBinanceRepository.save(accountBinance);
	    return sellDollarsRepository.save(sale);
	}


	
	@Override
	public List<SellDollars> obtenerVentasEntreFechas(LocalDateTime inicio, LocalDateTime fin){
		return sellDollarsRepository.findByDateBetween(inicio, fin);
	}
	
	
	
	@Override
	@Transactional
	public List<SellDollars> registrarYObtenerVentasNoAsignadas() {
	    Map<String, List<Transaction>> nuevasTransacciones = getTodayOutgoingUnregisteredTransactions();
	    saveNewSellDollars(nuevasTransacciones);
	    return sellDollarsRepository.findByAsignadoFalse();
	}

	
	
	// 1. Trae y filtra las transacciones válidas de Binance Pay
	private Map<String, List<Transaction>> getTodayOutgoingUnregisteredTransactions() {
	    Set<String> idsRegistrados = sellDollarsRepository.findAll()
	        .stream()
	        .map(SellDollars::getIdWithdrawals)
	        .collect(Collectors.toSet());

	    Set<String> binanceUsersRegistrados = accountBinanceRepository.findAll()
	        .stream()
	        .map(AccountBinance::getUserBinance)
	        .filter(Objects::nonNull)
	        .collect(Collectors.toSet());

	    Map<String, List<Transaction>> transaccionesValidasPorCuenta = new HashMap<>();

	    LocalDate hoy = LocalDate.now();

	    for (String cuenta : binanceService.getAllAccountNames()) {
	        String json = binanceService.getPaymentHistory(cuenta);
	        List<Transaction> transacciones = paymentController.parseTransactions(json);

	        List<Transaction> filtradas = transacciones.stream()
	        	    .filter(tx -> tx.getTransactionTime().toLocalDate().isEqual(hoy)) // ✅ Solo transacciones de hoy
	        	    .filter(tx -> tx.getAmount() < 0)
	        	    .filter(tx -> !idsRegistrados.contains(tx.getOrderId()))
	        	    .filter(tx -> tx.getReceiverInfo() == null || 
	        	                  !binanceUsersRegistrados.contains(tx.getReceiverInfo().getName()))
	        	    .collect(Collectors.toList());

	        if (!filtradas.isEmpty()) {
	            transaccionesValidasPorCuenta.put(cuenta, filtradas);
	        }
	    }

	    return transaccionesValidasPorCuenta;
	}


	
	// 2. Convierte y guarda transacciones como entidades SellDollars
	private List<SellDollars> saveNewSellDollars(Map<String, List<Transaction>> transaccionesPorCuenta) {
	    List<SellDollars> nuevasVentas = new ArrayList<>();

	    for (Map.Entry<String, List<Transaction>> entry : transaccionesPorCuenta.entrySet()) {
	        String nameAccount = entry.getKey();
	        List<Transaction> transacciones = entry.getValue();

	        for (Transaction tx : transacciones) {
	            SellDollars venta = new SellDollars();
	            venta.setIdWithdrawals(tx.getOrderId());
	            venta.setDate(tx.getTransactionTime());
	            venta.setDollars(Math.abs(tx.getAmount()));
	            venta.setTasa(0.0);
	            venta.setPesos(0.0);
	            venta.setNameAccount(nameAccount); // ✅ Aquí va el nombre correcto de la cuenta Binance
	            venta.setAsignado(false);

	            nuevasVentas.add(venta);
	        }
	    }

	    return sellDollarsRepository.saveAll(nuevasVentas);
	}
	
	private Double utilidad(SellDollars sell) {
		
		PurchaseRate lastRate = purchaseRateRepository.findTopByOrderByDateDesc();
		Double pesos = sell.getPesos();
		Double usdt = sell.getDollars();
		Double utilidadVenta = pesos - (usdt * lastRate.getRate() ) - (sell.getPesos() * 0.004);  
		
		return utilidadVenta;
	}

	
	//aqui terminara para obtener ventas de binance pay
	
	//metodo para obtener la utilidad de una rango de sell
	public void saveUtilityDefinitive(List<SellDollars> rangoSell, Double averageRate) {
		Double pesosUsdtVendidos = 0.0;
		Double usdtVendidos = 0.0;
		Double utilidad = 0.0;
		for (SellDollars sell: rangoSell) {
			pesosUsdtVendidos= sell.getPesos();
			usdtVendidos= sell.getDollars();
			utilidad = pesosUsdtVendidos - (usdtVendidos * averageRate);
			utilidad = utilidad - generateTax(sell);
			sell.setUtilidad(utilidad);
			sellDollarsRepository.save(sell);
		}
		
	}
	
	//metodo de apoyo para generar los impuestos de las cuentas colombianas
	public Double generateTax(SellDollars sell) {
		Double impuesto= 0.0;
		List<SellDollarsAccountCop> sellAccount = sell.getSellDollarsAccounts();
		for(SellDollarsAccountCop account: sellAccount) {
			if(account.getAccountCop()==null) {
				impuesto = account.getAmount()*0.004;
			} 
		}
		return impuesto;
	}
	

	
	@Override
	public List<SellDollars> obtenerVentasPorFecha(LocalDate fecha) {
	    LocalDateTime start = fecha.atStartOfDay();
	    LocalDateTime end = fecha.plusDays(1).atStartOfDay();
	    return sellDollarsRepository.findByDateBetween(start, end);
	}
	
	@Override
	public List<SellDollars> obtenerVentasPorCliente(Integer clienteId) {
	    return sellDollarsRepository.findByClienteId(clienteId);
	}
	
	@Override
	public List<SellDollarsDto> listarVentasDto() {
	    List<SellDollars> ventas = sellDollarsRepository.findAll();
	    return ventas.stream().map(this::convertToDto).collect(Collectors.toList());
	}

	private SellDollarsDto convertToDto(SellDollars venta) {
	    SellDollarsDto dto = new SellDollarsDto();
	    dto.setId(venta.getId());
	    dto.setIdWithdrawals(venta.getIdWithdrawals());
	    dto.setTasa(venta.getTasa());
	    dto.setDollars(venta.getDollars());
	    dto.setPesos(venta.getPesos());
	    dto.setDate(venta.getDate());
	    dto.setNameAccount(venta.getNameAccount());
	    dto.setAccountBinanceId(
	        venta.getAccountBinance() != null ? venta.getAccountBinance().getId() : null
	    );
	    dto.setSupplier(
	        venta.getSupplier() != null ? venta.getSupplier().getId() : null
	    );
	    dto.setClienteId(
	        venta.getCliente() != null ? venta.getCliente().getId() : null
	    );

	    if (venta.getSellDollarsAccounts() != null) {
	        List<String> nombres = venta.getSellDollarsAccounts().stream()
	            .map(detalle -> detalle.getAccountCop() != null ? detalle.getAccountCop().getName() : null)
	            .filter(Objects::nonNull) // eliminar nulos
	            .collect(Collectors.toList());
	        dto.setNombresCuentasAsignadas(nombres);
	    }



	    return dto;
	}
	
	
	@Transactional
	public SellDollars updateSellDollars(Integer id, SellDollarsDto dto) {
	    SellDollars existing = sellDollarsRepository.findById(id)
	        .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

	    // 1️⃣ Revertir efectos anteriores:
	    revertPreviousAssignment(existing);

	    // 2️⃣ Aplicar nuevos valores:
	    // usa lógica similar a createSellDollars, actualizando por ejemplo:
	    existing.setTasa(dto.getTasa());
	    existing.setDollars(dto.getDollars());
	    existing.setPesos(dto.getPesos());
	    // actualiza cliente o supplier según dto.getClienteId() / dto.getSupplier()
	    // también actualiza cuentas COP

	    // 3️⃣ Guardar efectos nuevos:
	    applyNewAssignment(existing, dto);

	    return sellDollarsRepository.save(existing);
	}
	
	private void revertPreviousAssignment(SellDollars sell) {
	    if (sell.getCliente() != null) {
	        Cliente cl = sell.getCliente();
	        cl.setSaldo(cl.getSaldo() + sell.getPesos());
	        clienteRepository.save(cl);
	    } else if (sell.getSupplier() != null) {
	        Supplier sup = sell.getSupplier();
	        sup.setBalance(sup.getBalance() + (sell.getPesos() - sumAccounts(sell)));
	        supplierRepository.save(sup);
	        // revertir cuentas COP
	        for (SellDollarsAccountCop det : sell.getSellDollarsAccounts()) {
	            AccountCop acc = det.getAccountCop();
	            acc.setBalance(acc.getBalance() - det.getAmount());
	            accountCopService.saveAccountCop(acc);
	        }
	    }
	}
	
	private double sumAccounts(SellDollars sell) {
	    if (sell.getSellDollarsAccounts() == null) return 0.0;
	    return sell.getSellDollarsAccounts().stream()
	            .mapToDouble(SellDollarsAccountCop::getAmount)
	            .sum();
	}
	
	private void applyNewAssignment(SellDollars existing, SellDollarsDto dto) {
	    existing.setAsignado(true);
	    existing.setUtilidad(utilidad(existing));

	    // Asignación por cliente
	    if (dto.getClienteId() != null) {
	        Cliente cliente = clienteRepository.findById(dto.getClienteId())
	                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

	        existing.setCliente(cliente);
	        existing.setSupplier(null);

	        double saldoActual = cliente.getSaldo() != null ? cliente.getSaldo() : 0.0;
	        cliente.setSaldo(saldoActual - dto.getPesos());
	        clienteRepository.save(cliente);

	        // Limpia correctamente la colección para evitar error Hibernate
	        if (existing.getSellDollarsAccounts() != null) {
	            existing.getSellDollarsAccounts().forEach(det -> det.setSellDollars(null));
	            existing.getSellDollarsAccounts().clear();
	        }

	    } else {
	        // Asignación a proveedor y cuentas COP
	        Supplier supplier = supplierRepository.findById(dto.getSupplier())
	                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));

	        existing.setSupplier(supplier);
	        existing.setCliente(null);

	        double totalAsignadoACuentas = 0.0;
	        List<SellDollarsAccountCop> nuevosDetalles = new ArrayList<>();

	        if (dto.getAccounts() != null && !dto.getAccounts().isEmpty()) {
	            for (AssignAccountDto assignDto : dto.getAccounts()) {
	                AccountCop acc = accountCopService.findByIdAccountCop(assignDto.getAccountCop());

	                double currentBalance = acc.getBalance() != null ? acc.getBalance() : 0.0;
	                acc.setBalance(currentBalance + assignDto.getAmount());
	                accountCopService.saveAccountCop(acc);

	                SellDollarsAccountCop detalle = new SellDollarsAccountCop();
	                detalle.setSellDollars(existing);
	                detalle.setAccountCop(acc);
	                detalle.setAmount(assignDto.getAmount());
	                detalle.setNameAccount(assignDto.getNameAccount());

	                nuevosDetalles.add(detalle);
	                totalAsignadoACuentas += assignDto.getAmount();
	            }
	        }

	        double montoEnPesos = dto.getPesos() != null ? dto.getPesos() : dto.getDollars() * dto.getTasa();
	        double restanteParaProveedor = montoEnPesos - totalAsignadoACuentas;

	        if (restanteParaProveedor > 0) {
	            double currentSupplierBalance = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
	            supplier.setBalance(currentSupplierBalance - restanteParaProveedor);
	            supplierRepository.save(supplier);
	        }

	        // Limpieza correcta antes de asignar los nuevos
	        if (existing.getSellDollarsAccounts() != null) {
	            existing.getSellDollarsAccounts().forEach(det -> det.setSellDollars(null));
	            existing.getSellDollarsAccounts().clear();
	        } else {
	            existing.setSellDollarsAccounts(new ArrayList<>());
	        }

	        existing.getSellDollarsAccounts().addAll(nuevosDetalles);
	    }
	}

}
