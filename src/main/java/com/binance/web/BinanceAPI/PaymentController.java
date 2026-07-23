package com.binance.web.BinanceAPI;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Cliente;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.binance.web.model.BuyDollarsDto;
import com.binance.web.model.SellDollarsDto;
import com.binance.web.model.Transaction;
import com.binance.web.transacciones.TransaccionesDTO;
import com.binance.web.transacciones.TransaccionesRepository;

@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PaymentController {

	private static final String nameAccount = "account";

	@Autowired private BinanceService binanceService;
	@Autowired private AccountBinanceRepository accountBinanceRepository;
	@Autowired private BuyDollarsRepository buyDollarsRepository;
	@Autowired private SellDollarsRepository sellDollarsRepository;
	@Autowired private TransaccionesRepository transaccionesRepository;
	@Autowired private ClienteRepository clienteRepository;

	@GetMapping("/payments")
	public ResponseEntity<String> getPaymentHistory(@RequestParam(nameAccount) String account) {
		String response = binanceService.getPaymentHistory(account);
		return ResponseEntity.ok().body(response);
	}

	public List<Transaction> parseTransactions(String response) {
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			JsonNode rootNode = objectMapper.readTree(response);
			JsonNode dataNode = rootNode.path("data");
			if (dataNode.isArray() && dataNode.size() > 0) {
				List<Transaction> transactions = new ArrayList<>();
				for (JsonNode node : dataNode) {
					transactions.add(objectMapper.treeToValue(node, Transaction.class));
				}
				return transactions;
			}
			return new ArrayList<>();
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Error parsing BinancePay transactions", e);
		}
	}

	/**
	 * DIAGNÓSTICO (solo lectura): lista TODOS los movimientos de Binance Pay de una cuenta
	 * (sin filtrar por día/tipo), con todos los campos relevantes. Sirve para inspeccionar
	 * movimientos raros como el "pago atrasado" de una apelación resuelta y ver con qué
	 * orderType / payer / receiver / id vienen, para luego construir el filtro correcto.
	 * Ej: GET /api/binancepay-diagnostico?account=Luis
	 */
	@RequestMapping(value = "/binancepay-diagnostico", method = { RequestMethod.GET, RequestMethod.POST })
	public ResponseEntity<List<Map<String, Object>>> diagnosticoBinancePay(@RequestParam(nameAccount) String account) {
		List<Map<String, Object>> out = new ArrayList<>();
		try {
			for (Transaction tx : parseTransactions(binanceService.getPaymentHistory(account))) {
				Map<String, Object> row = new java.util.LinkedHashMap<>();
				row.put("fecha", tx.getTransactionTime());
				row.put("monto", tx.getAmount());
				row.put("moneda", tx.getCurrency());
				row.put("orderType", tx.getOrderType());
				row.put("orderId", tx.getOrderId());
				row.put("transactionId", tx.getTransactionId());
				row.put("payer", tx.getPayerInfo() != null ? tx.getPayerInfo().getName() : null);
				row.put("receiver", tx.getReceiverInfo() != null ? tx.getReceiverInfo().getName() : null);
				row.put("receiverBinanceId", tx.getReceiverInfo() != null ? tx.getReceiverInfo().getBinanceId() : null);
				out.add(row);
			}
		} catch (Exception e) {
			log.error("Error en diagnosticoBinancePay ({}): {}", account, e.getMessage(), e);
			return ResponseEntity.status(500).body(out);
		}
		return ResponseEntity.ok(out);
	}

	/**
	 * DIAGNÓSTICO (solo lectura): igual que el anterior pero barriendo TODAS las cuentas de una
	 * sola vez, etiquetando de cuál viene cada movimiento. Sirve para localizar un movimiento
	 * concreto (ej. el "pago atrasado" de −154.53) sin adivinar en qué cuenta está.
	 * Ej: GET /api/binancepay-diagnostico-todas
	 */
	@RequestMapping(value = "/binancepay-diagnostico-todas", method = { RequestMethod.GET, RequestMethod.POST })
	public ResponseEntity<List<Map<String, Object>>> diagnosticoBinancePayTodas() {
		List<Map<String, Object>> out = new ArrayList<>();
		try {
			for (String cuenta : binanceService.getAllAccountNames()) {
				try {
					for (Transaction tx : parseTransactions(binanceService.getPaymentHistory(cuenta))) {
						Map<String, Object> row = new java.util.LinkedHashMap<>();
						row.put("cuenta", cuenta);
						row.put("fecha", tx.getTransactionTime());
						row.put("monto", tx.getAmount());
						row.put("moneda", tx.getCurrency());
						row.put("orderType", tx.getOrderType());
						row.put("orderId", tx.getOrderId());
						row.put("transactionId", tx.getTransactionId());
						row.put("payer", tx.getPayerInfo() != null ? tx.getPayerInfo().getName() : null);
						row.put("receiver", tx.getReceiverInfo() != null ? tx.getReceiverInfo().getName() : null);
						out.add(row);
					}
				} catch (Exception e) {
					log.warn("[DIAG] Binance Pay falló en cuenta {}: {}", cuenta, e.getMessage());
				}
			}
		} catch (Exception e) {
			log.error("Error en diagnosticoBinancePayTodas: {}", e.getMessage(), e);
			return ResponseEntity.status(500).body(out);
		}
		return ResponseEntity.ok(out);
	}

	@GetMapping("/compras-binancepay")
	public ResponseEntity<List<BuyDollarsDto>> getComprasNoRegistradas() {
		List<BuyDollarsDto> resultados = new ArrayList<>();
		try {
			LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
			Set<String> userBinanceValidos = accountBinanceRepository.findAllUserBinances();
			Set<String> idsRegistrados     = buyDollarsRepository.findAllDepositIds();

			for (String cuenta : binanceService.getAllAccountNames()) {
				for (Transaction tx : parseTransactions(binanceService.getPaymentHistory(cuenta))) {
					double monto = tx.getAmount();
					LocalDateTime fecha = tx.getTransactionTime();
					if (monto <= 0 || idsRegistrados.contains(tx.getOrderId())
							|| fecha == null || !fecha.toLocalDate().isEqual(hoy)) continue;
					if (tx.getPayerInfo() == null || userBinanceValidos.contains(tx.getPayerInfo().getName())) continue;

					BuyDollarsDto dto = new BuyDollarsDto();
					dto.setIdDeposit(tx.getOrderId());
					dto.setNameAccount(cuenta);
					dto.setDate(fecha);
					dto.setAmount(monto);
					dto.setTasa(0.0);
					dto.setPesos(0.0);
					dto.setCryptoSymbol(tx.getCurrency());
					resultados.add(dto);
				}
			}
		} catch (Exception e) {
			log.error("Error en getComprasNoRegistradas: {}", e.getMessage(), e);
			return ResponseEntity.status(500).body(new ArrayList<>());
		}
		return ResponseEntity.ok(resultados);
	}

	@GetMapping("/compras-binancepay/{fecha}")
	public ResponseEntity<List<BuyDollarsDto>> getComprasPorFecha(@PathVariable String fecha) {
		LocalDate fechaFiltro = LocalDate.parse(fecha);
		List<BuyDollarsDto> resultados = new ArrayList<>();
		try {
			Set<String> userBinanceValidos = accountBinanceRepository.findAllUserBinances();
			Set<String> idsRegistrados     = buyDollarsRepository.findAllDepositIds();

			for (String cuenta : binanceService.getAllAccountNames()) {
				for (Transaction tx : parseTransactions(binanceService.getPaymentHistory(cuenta))) {
					double monto = tx.getAmount();
					LocalDateTime fechaTx = tx.getTransactionTime();
					if (monto <= 0 || idsRegistrados.contains(tx.getOrderId())
							|| fechaTx == null || !fechaTx.toLocalDate().isEqual(fechaFiltro)) continue;
					if (tx.getPayerInfo() == null || userBinanceValidos.contains(tx.getPayerInfo().getName())) continue;

					BuyDollarsDto dto = new BuyDollarsDto();
					dto.setIdDeposit(tx.getOrderId());
					dto.setNameAccount(cuenta);
					dto.setDate(fechaTx);
					dto.setAmount(monto);
					dto.setTasa(0.0);
					dto.setPesos(0.0);
					dto.setCryptoSymbol(tx.getCurrency());
					resultados.add(dto);

					BuyDollars entidad = new BuyDollars();
					entidad.setIdDeposit(tx.getOrderId());
					entidad.setNameAccount(cuenta);
					entidad.setDate(fechaTx);
					entidad.setAmount(monto);
					entidad.setTasa(0.0);
					entidad.setPesos(0.0);
					entidad.setCryptoSymbol(tx.getCurrency());
					buyDollarsRepository.save(entidad);
				}
			}
		} catch (Exception e) {
			log.error("Error en getComprasPorFecha ({}): {}", fecha, e.getMessage(), e);
			return ResponseEntity.status(500).body(new ArrayList<>());
		}
		return ResponseEntity.ok(resultados);
	}

	@GetMapping("/ventas-no-registradas-binancepay")
	public ResponseEntity<List<SellDollarsDto>> getVentasNoRegistradasBinancePay() {
		List<SellDollarsDto> resultados = new ArrayList<>();
		try {
			LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
			Set<String> idsRegistrados    = sellDollarsRepository.findAllWithdrawalIds();
			Set<String> userBinanceValidos = accountBinanceRepository.findAllUserBinances();

			Map<Long, Cliente> clientePorBinanceId = clienteRepository.findByBinanceIdNotNull().stream()
					.collect(Collectors.toMap(Cliente::getBinanceId, Function.identity()));

			for (String cuenta : binanceService.getAllAccountNames()) {
				for (Transaction tx : parseTransactions(binanceService.getPaymentHistory(cuenta))) {
					double monto = tx.getAmount();
					LocalDateTime fecha = tx.getTransactionTime();
					if (monto >= 0 || idsRegistrados.contains(tx.getOrderId())
							|| fecha == null || !fecha.toLocalDate().isEqual(hoy)) continue;
					if (tx.getReceiverInfo() == null || userBinanceValidos.contains(tx.getReceiverInfo().getName())) continue;

					SellDollarsDto dto = new SellDollarsDto();
					dto.setIdWithdrawals(tx.getOrderId());
					dto.setNameAccount(cuenta);
					dto.setDate(fecha);
					dto.setDollars(Math.abs(monto));
					dto.setTasa(0.0);
					dto.setPesos(0.0);

					Long recvBinanceId = tx.getReceiverInfo().getBinanceId();
					Cliente cliente = clientePorBinanceId.get(recvBinanceId);
					if (cliente != null) dto.setClienteId(cliente.getId());

					resultados.add(dto);
				}
			}
			return ResponseEntity.ok(resultados);
		} catch (Exception e) {
			log.error("Error en getVentasNoRegistradasBinancePay: {}", e.getMessage(), e);
			return ResponseEntity.status(500).body(new ArrayList<>());
		}
	}

	@GetMapping("/transacciones-binacepay")
	public ResponseEntity<List<TransaccionesDTO>> getTransaccionesNoRegistradas() {
		List<TransaccionesDTO> resultados = new ArrayList<>();
		try {
			Set<String> userBinanceValidos = accountBinanceRepository.findAllUserBinances();
			Set<String> idsRegistrados     = transaccionesRepository.findAllTransaccionIds();

			for (String cuenta : binanceService.getAllAccountNames()) {
				for (Transaction tx : parseTransactions(binanceService.getPaymentHistory(cuenta))) {
					double monto = tx.getAmount();
					if (monto >= 0 || idsRegistrados.contains(tx.getOrderId())) continue;
					if (tx.getReceiverInfo() == null || !userBinanceValidos.contains(tx.getReceiverInfo().getName())) continue;

					TransaccionesDTO dto = new TransaccionesDTO();
					dto.setIdtransaccion(tx.getOrderId());
					dto.setCuentaFrom(cuenta);
					dto.setCuentaTo(tx.getReceiverInfo().getName());
					dto.setFecha(tx.getTransactionTime());
					dto.setMonto(Math.abs(monto));
					dto.setTipo("BINANCEPAY");
					resultados.add(dto);
				}
			}
		} catch (Exception e) {
			log.error("Error en getTransaccionesNoRegistradas: {}", e.getMessage(), e);
			return ResponseEntity.status(500).body(new ArrayList<>());
		}
		return ResponseEntity.ok(resultados);
	}
}
