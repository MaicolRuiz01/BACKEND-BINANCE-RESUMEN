package com.binance.web.SellDollars;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.binance.web.accountCryptoBalance.AccountCryptoBalanceService;
import com.binance.web.model.Transaction;
import com.binance.web.AccountCop.AccountCopService;
import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.BinanceAPI.PaymentController;
import com.binance.web.BinanceAPI.SolanaController;
import com.binance.web.BinanceAPI.SpotOrdersController;
import com.binance.web.BinanceAPI.TronScanController;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.PurchaseRate;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.SellDollarsAccountCop;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.AverageRateRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.PurchaseRateRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.Entity.Supplier;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SellDollarsServiceImpl implements SellDollarsService {

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
	@Autowired
	private SpotOrdersController spotOrdersController;
	@Autowired
	private PaymentController binancePayController;
	@Autowired
	private TronScanController tronScanController;
	@Autowired
	private AverageRateRepository averageRateRepository;
	@Autowired
	private AccountCryptoBalanceService accountCryptoBalanceService;
	@Autowired
	private SolanaController solanaController;

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
		// 3. Ajustar saldo de la cuenta Binance
		// double currentBinanceBalance = accountBinance.getBalance() != null ?
		// accountBinance.getBalance() : 0.0;
		// accountBinance.setBalance(currentBinanceBalance - dto.getDollars());

		// ⚡ Caso 1: Si hay cliente, se descuenta del saldo del cliente
		if (dto.getClienteId() != null) {
			Cliente cliente = clienteRepository.findById(dto.getClienteId())
					.orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
			sale.setCliente(cliente);
			sale.setSupplier(null); // 🔥 Importante

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

			double montoEnPesos = dto.getPesos() != null ? dto.getPesos() : dto.getDollars() * dto.getTasa();
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

	// src/main/java/com/binance/web/Service/Impl/SellDollarsServiceImpl.java

	@Override
	@Transactional
	public SellDollars asignarVenta(Integer id, SellDollarsDto dto) {
		// 1. Buscar la venta no asignada
		SellDollars existing = sellDollarsRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Venta no encontrada"));

		if (Boolean.TRUE.equals(existing.getAsignado())) {
			throw new RuntimeException("Venta ya fue asignada");

		}
		// 2. Actualizar datos básicos
		existing.setTasa(dto.getTasa());
		existing.setPesos(dto.getDollars() * dto.getTasa());

		AverageRate tasaPromedioAnterior = averageRateRepository
				.findTopByFechaBeforeOrderByFechaDesc(existing.getDate()).orElse(null);

		if (tasaPromedioAnterior != null) {
			Double costoEnPesos = existing.getDollars() * tasaPromedioAnterior.getAverageRate();
			Double comision = existing.getComision() != null ? existing.getComision() : 0.0;
			Double utilidad = existing.getPesos() - costoEnPesos - comision;
			existing.setUtilidad(utilidad);
		} else {
			// En caso de que no haya una tasa anterior, la utilidad es 0
			existing.setUtilidad(0.0);
		}

		// 3. Limpiar relaciones anteriores
		// ⚠️ Importante: Limpiamos la colección para que Hibernate elimine las viejas
		// cuentas COP.
		if (existing.getSellDollarsAccounts() != null) {
			existing.getSellDollarsAccounts().clear();
		}

		// 4. Lógica para cliente o proveedor
		if (dto.getClienteId() != null) {
			Cliente cliente = clienteRepository.findById(dto.getClienteId())
					.orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
			existing.setCliente(cliente);
			existing.setSupplier(null);

			double saldoActual = cliente.getSaldo() != null ? cliente.getSaldo() : 0.0;
			cliente.setSaldo(saldoActual - existing.getPesos());
			clienteRepository.save(cliente);

		} else {
			Supplier supplier = supplierRepository.findById(dto.getSupplier())
					.orElseThrow(() -> new RuntimeException("Supplier no encontrado"));
			existing.setSupplier(supplier);
			existing.setCliente(null);

			double totalAsignadoACuentas = 0.0;

			// ✅ AHORA AGREGAMOS LAS NUEVAS CUENTAS A LA COLECCIÓN
			if (dto.getAccounts() != null && !dto.getAccounts().isEmpty()) {
				for (AssignAccountDto assignDto : dto.getAccounts()) {
					AccountCop acc = accountCopService.findByIdAccountCop(assignDto.getAccountCop());

					double currentCopBalance = acc.getBalance() != null ? acc.getBalance() : 0.0;
					acc.setBalance(currentCopBalance + assignDto.getAmount());
					accountCopService.saveAccountCop(acc);

					SellDollarsAccountCop detalle = new SellDollarsAccountCop();
					detalle.setSellDollars(existing); // Establecemos la relación bidireccional
					detalle.setAccountCop(acc);
					detalle.setAmount(assignDto.getAmount());
					detalle.setNameAccount(assignDto.getNameAccount());

					// ✅ Agregamos el nuevo objeto a la colección
					existing.getSellDollarsAccounts().add(detalle);

					totalAsignadoACuentas += assignDto.getAmount();
				}
			}

			// 5. Ajustar saldo del proveedor
			double montoEnPesos = existing.getPesos();
			double restanteParaProveedor = montoEnPesos - totalAsignadoACuentas;

			if (restanteParaProveedor > 0.0) {
				double currentSupplierBalance = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
				supplier.setBalance(currentSupplierBalance - restanteParaProveedor);
			}
			supplierRepository.save(supplier);
		}

		// 6. Marcar como asignada
		existing.setAsignado(true);

		// 7. Descontar balance en la cuenta Binance (cripto vendida)
		String symbol = dto.getCryptoSymbol() != null ? dto.getCryptoSymbol() : "USDT";

		// 1) Descontar SOLO el token vendido
		accountCryptoBalanceService.updateCryptoBalance(existing.getAccountBinance(), symbol, -dto.getDollars());

		// 2) Si viene fee de Solana, descuéntalo en SOL
		if (dto.getNetworkFeeInSOL() != null && dto.getNetworkFeeInSOL() > 0) {
			accountCryptoBalanceService.updateCryptoBalance(existing.getAccountBinance(), "SOL",
					-dto.getNetworkFeeInSOL());
		}

		// 3) Si viene fee TRON (tu campo comision lo usas para TRX), descuéntalo en TRX
		if (dto.getComision() != null && dto.getComision() > 0) {
			accountCryptoBalanceService.updateCryptoBalance(existing.getAccountBinance(), "TRX", -dto.getComision());
		}

		return sellDollarsRepository.save(existing);
	}

	@Override
	public List<SellDollars> obtenerVentasEntreFechas(LocalDateTime inicio, LocalDateTime fin) {
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
		Set<String> idsRegistrados = sellDollarsRepository.findAll().stream().map(SellDollars::getIdWithdrawals)
				.collect(Collectors.toSet());

		Set<String> binanceUsersRegistrados = accountBinanceRepository.findAll().stream()
				.map(AccountBinance::getUserBinance).filter(Objects::nonNull).collect(Collectors.toSet());

		Map<String, List<Transaction>> transaccionesValidasPorCuenta = new HashMap<>();

		LocalDate hoy = LocalDate.now();

		for (String cuenta : binanceService.getAllAccountNames()) {
			String json = binanceService.getPaymentHistory(cuenta);
			List<Transaction> transacciones = paymentController.parseTransactions(json);

			List<Transaction> filtradas = transacciones.stream()
					.filter(tx -> tx.getTransactionTime().toLocalDate().isEqual(hoy)) // ✅ Solo transacciones de hoy
					.filter(tx -> tx.getAmount() < 0).filter(tx -> !idsRegistrados.contains(tx.getOrderId()))
					.filter(tx -> tx.getReceiverInfo() == null
							|| !binanceUsersRegistrados.contains(tx.getReceiverInfo().getName()))
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

	// metodo de apoyo para generar los impuestos de las cuentas colombianas
	public Double generateTax(SellDollars sell) {
		Double impuesto = 0.0;
		List<SellDollarsAccountCop> sellAccount = sell.getSellDollarsAccounts();
		for (SellDollarsAccountCop account : sellAccount) {
			if (account.getAccountCop() == null) {
				impuesto = account.getAmount() * 0.004;
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
		dto.setAccountBinanceId(venta.getAccountBinance() != null ? venta.getAccountBinance().getId() : null);
		dto.setSupplier(venta.getSupplier() != null ? venta.getSupplier().getId() : null);
		dto.setClienteId(venta.getCliente() != null ? venta.getCliente().getId() : null);
		dto.setEquivalenteciaTRX(venta.getEquivalenteciaTRX()); // ✅ Se añade la conversión a DTO

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
		if (sell.getSellDollarsAccounts() == null)
			return 0.0;
		return sell.getSellDollarsAccounts().stream().mapToDouble(SellDollarsAccountCop::getAmount).sum();
	}

	private void applyNewAssignment(SellDollars existing, SellDollarsDto dto) {
		existing.setAsignado(true);

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

	@Override
	@Transactional
	public void registrarVentasAutomaticamente() {

		try {
			List<SellDollarsDto> spot = spotOrdersController.getVentasNoRegistradas(50).getBody();
			List<SellDollarsDto> binancePay = binancePayController.getVentasNoRegistradasBinancePay().getBody();
			List<SellDollarsDto> trust = tronScanController.getUSDTOutgoingTransfers().getBody();
			List<SellDollarsDto> sol = solanaController.getSolanaOutgoingTransfers().getBody();

			Set<String> existentes = sellDollarsRepository.findAll().stream().map(SellDollars::getIdWithdrawals)
					.filter(Objects::nonNull).collect(Collectors.toSet());

			List<SellDollarsDto> todas = new ArrayList<>();
			if (spot != null)
				todas.addAll(spot);
			if (binancePay != null)
				todas.addAll(binancePay);
			if (trust != null)
				todas.addAll(trust);
			if (sol != null)
				todas.addAll(sol);

			todas.sort(Comparator.comparing(SellDollarsDto::getDate));

			for (SellDollarsDto dto : todas) {
				if (dto.getIdWithdrawals() == null || existentes.contains(dto.getIdWithdrawals()))
					continue;

				AccountBinance account = accountBinanceRepository.findByName(dto.getNameAccount());
				// if (account == null) continue;

				SellDollars nueva = new SellDollars();
				nueva.setIdWithdrawals(dto.getIdWithdrawals());
				nueva.setNameAccount(dto.getNameAccount());
				nueva.setDate(dto.getDate());

				// ✅ LÓGICA CORREGIDA: Asignar valores de dólares y TRX
				nueva.setDollars(dto.getDollars());
				nueva.setEquivalenteciaTRX(dto.getEquivalenteciaTRX());

				nueva.setTasa(0.0);
				nueva.setPesos(0.0);
				nueva.setAsignado(false);
				nueva.setAccountBinance(account);
				nueva.setComision(dto.getComision());

				String symbol = dto.getCryptoSymbol() != null ? dto.getCryptoSymbol() : "USDT";

				// token vendido
				accountCryptoBalanceService.updateCryptoBalance(account, symbol, -dto.getDollars());

				// fee de Solana (si aplica)
				if (dto.getNetworkFeeInSOL() != null && dto.getNetworkFeeInSOL() > 0) {
				    accountCryptoBalanceService.updateCryptoBalance(account, "SOL", -dto.getNetworkFeeInSOL());
				}

				// fee de TRON (si aplica)
				if (dto.getComision() != null && dto.getComision() > 0) {
				    accountCryptoBalanceService.updateCryptoBalance(account, "TRX", -dto.getComision());
				}


				sellDollarsRepository.save(nueva);
			}
		} catch (Exception e) {
			// TODO: handle exception
			throw new RuntimeException("Error al registrar compras automáticamente", e);
		}

	}

}
