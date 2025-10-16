package com.binance.web.SellDollars;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.SellDollarsAccountCop;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.AverageRateRepository;
import com.binance.web.Repository.ClienteRepository;
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
	    SellDollars existing = sellDollarsRepository.findById(id)
	            .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

	    if (Boolean.TRUE.equals(existing.getAsignado())) {
	        throw new RuntimeException("Venta ya fue asignada");
	    }

	    // Asegura colección de detalles
	    if (existing.getSellDollarsAccounts() == null) {
	        existing.setSellDollarsAccounts(new ArrayList<>());
	    } else {
	        existing.getSellDollarsAccounts().clear();
	    }

	    final boolean esSolana = isSolanaSale(existing);

	    double pesosAsignados = 0.0;

	    // 1) Siempre asignamos el dinero recibido a las cuentas COP indicadas en el DTO
	    if (dto.getAccounts() != null && !dto.getAccounts().isEmpty()) {
	        for (AssignAccountDto assignDto : dto.getAccounts()) {
	            AccountCop acc = accountCopService.findByIdAccountCop(assignDto.getAccountCop());
	            double current = acc.getBalance() != null ? acc.getBalance() : 0.0;
	            double monto = assignDto.getAmount() != null ? assignDto.getAmount() : 0.0;

	            acc.setBalance(current + monto);
	            accountCopService.saveAccountCop(acc);

	            SellDollarsAccountCop det = new SellDollarsAccountCop();
	            det.setSellDollars(existing);
	            det.setAccountCop(acc);
	            det.setAmount(monto);
	            det.setNameAccount(assignDto.getNameAccount());
	            existing.getSellDollarsAccounts().add(det);

	            pesosAsignados += monto;
	        }
	    }

	    if (esSolana) {
	        // 2) SOLANA: no cliente/proveedor; tasa se calcula a partir de lo asignado en COP
	        existing.setCliente(null);
	        existing.setSupplier(null);

	        existing.setPesos(pesosAsignados);
	        double dollars = existing.getDollars() != null ? existing.getDollars() : 0.0;
	        double tasaCalc = (dollars > 0.0) ? (pesosAsignados / dollars) : 0.0;
	        existing.setTasa(tasaCalc);

	        // 3) Utilidad contra tasa promedio del día anterior
	        AverageRate tasaPromedioAnterior = averageRateRepository
	                .findTopByFechaBeforeOrderByFechaDesc(existing.getDate())
	                .orElse(null);

	        if (tasaPromedioAnterior != null) {
	            double costoEnPesos = dollars * tasaPromedioAnterior.getAverageRate();
	            double comision = existing.getComision() != null ? existing.getComision() : 0.0; // fee de red (opcional)
	            existing.setUtilidad(existing.getPesos() - costoEnPesos - comision);
	        } else {
	            existing.setUtilidad(0.0);
	        }

	    } else {
	        // === FLUJO NORMAL (no SOLANA): igual que tenías ===
	        existing.setTasa(dto.getTasa());
	        existing.setPesos(existing.getDollars() * dto.getTasa());

	        AverageRate tasaPromedioAnterior = averageRateRepository
	                .findTopByFechaBeforeOrderByFechaDesc(existing.getDate()).orElse(null);

	        if (tasaPromedioAnterior != null) {
	            Double costoEnPesos = existing.getDollars() * tasaPromedioAnterior.getAverageRate();
	            Double comision = existing.getComision() != null ? existing.getComision() : 0.0;
	            Double utilidad = existing.getPesos() - costoEnPesos - comision;
	            existing.setUtilidad(utilidad);
	        } else {
	            existing.setUtilidad(0.0);
	        }

	        // Cliente o proveedor (solo COP/deuda) – como lo tenías
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

	            double totalAsignadoACuentas = pesosAsignados; // ya acumulado arriba
	            double restanteParaProveedor = existing.getPesos() - totalAsignadoACuentas;
	            if (restanteParaProveedor > 0.0) {
	                double currentSupplierBalance = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
	                supplier.setBalance(currentSupplierBalance - restanteParaProveedor);
	            }
	            supplierRepository.save(supplier);
	        }
	    }

	    existing.setAsignado(true);
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

		LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

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
		
		dto.setTipoCuenta(venta.getTipoCuenta() != null ? venta.getTipoCuenta() : null);

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
	        List<SellDollarsDto> spot       = spotOrdersController.getVentasNoRegistradas(50).getBody();
	        List<SellDollarsDto> binancePay = binancePayController.getVentasNoRegistradasBinancePay().getBody();
	        List<SellDollarsDto> trust      = tronScanController.getUSDTOutgoingTransfers().getBody();
	        List<SellDollarsDto> sol        = solanaController.getSolanaOutgoingTransfers().getBody();

	        // IDs que YA existen en DB
	        Set<String> existentes = sellDollarsRepository.findAll().stream()
	                .map(SellDollars::getIdWithdrawals)
	                .filter(Objects::nonNull)
	                .collect(Collectors.toSet());

	        // Set vivo para evitar duplicados dentro del mismo lote
	        Set<String> vistos = new HashSet<>(existentes);

	        // Unifica y ordena
	        List<SellDollarsDto> todas = new ArrayList<>();
	        if (spot != null)       todas.addAll(spot);
	        if (binancePay != null) todas.addAll(binancePay);
	        if (trust != null)      todas.addAll(trust);
	        if (sol != null)        todas.addAll(sol);

	        todas.sort(Comparator.comparing(SellDollarsDto::getDate, Comparator.nullsLast(Comparator.naturalOrder())));

	        for (SellDollarsDto dto : todas) {

	            // 1) Valida ID y evita duplicados (DB y lote actual)
	            String idw = dto.getIdWithdrawals();
	            if (idw == null || idw.isBlank() || !vistos.add(idw)) {
	                continue;
	            }

	            // 2) Busca cuenta por nombre
	            AccountBinance account = accountBinanceRepository.findByName(dto.getNameAccount());

	            // 3) Symbol con fallback
	            String symbol = (dto.getCryptoSymbol() != null && !dto.getCryptoSymbol().isBlank())
	                    ? dto.getCryptoSymbol().trim().toUpperCase()
	                    : "USDT";

	            // 4) Montos seguros (por si vienen nulos)
	            double dollars   = dto.getDollars() != null ? dto.getDollars() : 0.0;
	            double feeSOL    = dto.getNetworkFeeInSOL() != null ? dto.getNetworkFeeInSOL() : 0.0;
	            double feeTRX    = dto.getComision() != null ? dto.getComision() : 0.0;
	            Double eqTRX     = dto.getEquivalenteciaTRX(); // si viene nulo, lo dejamos nulo (según tu entidad)

	            // 5) Ajusta balances cripto (no debe tumbar la importación si falla)
	            try {
	                if (account != null) {
	                    // saldo vendido (permitimos negativo SOLO en import automático)
	                    accountCryptoBalanceService.updateCryptoBalance(account, symbol, -dollars, true);

	                    if (feeSOL > 0) {
	                        accountCryptoBalanceService.updateCryptoBalance(account, "SOL", -feeSOL, true);
	                    }
	                    if (feeTRX > 0) {
	                        accountCryptoBalanceService.updateCryptoBalance(account, "TRX", -feeTRX, true);
	                    }
	                } else {
	                    System.out.println("⚠️ Cuenta no encontrada por name: " + dto.getNameAccount() + " (se registra venta igual)");
	                }
	            } catch (Exception ex) {
	                System.out.println("⚠️ No se pudo ajustar balance cripto: " + ex.getMessage());
	                // NO relanzar aquí para no marcar rollback-only
	            }

	            // 6) tipoCuenta con fallback: primero de la cuenta, si no del dto
	            String tipoCuenta = (account != null && account.getTipo() != null && !account.getTipo().isBlank())
	                    ? account.getTipo()
	                    : dto.getTipoCuenta();

	            // 7) Construye entidad y setea campos obligatorios
	            SellDollars nueva = new SellDollars();
	            nueva.setIdWithdrawals(idw);
	            nueva.setNameAccount(dto.getNameAccount());
	            nueva.setDate(dto.getDate());
	            nueva.setDollars(dollars);
	            nueva.setEquivalenteciaTRX(eqTRX);
	            nueva.setTasa(0.0);
	            nueva.setPesos(0.0);
	            nueva.setAsignado(false);
	            nueva.setAccountBinance(account);
	            nueva.setComision(feeTRX);
	            nueva.setTipoCuenta(tipoCuenta);
	            nueva.setCryptoSymbol(symbol);

	            // Si tu columna UTILIDAD es NOT NULL en DB, inicialízala:
	            try {
	                nueva.setUtilidad(0.0); // <-- comenta esta línea si tu entidad no tiene 'utilidad'
	            } catch (NoSuchMethodError | Exception ignore) {
	                // por si la entidad no tiene el setter en tu versión
	            }

	            // 8) Guarda
	            sellDollarsRepository.save(nueva);
	            // (opcional) forzar flush para detectar problemas en este punto:
	            // sellDollarsRepository.flush();
	        }

	    } catch (Exception e) {
	        // Relanzar con contexto; si algo revienta aquí, que se vea la causa real.
	        throw new RuntimeException("Error al registrar ventas automáticamente", e);
	    }
	}

	private boolean isSolanaSale(SellDollars s) {
	    AccountBinance src = s.getAccountBinance();
	    if (src == null && s.getNameAccount() != null) {
	        src = accountBinanceRepository.findByName(s.getNameAccount());
	    }
	    String tipo = (src != null && src.getTipo() != null) ? src.getTipo() : "";
	    return tipo.equalsIgnoreCase("SOLANA") || tipo.equalsIgnoreCase("PHANTOM");
	}
	
	

}
