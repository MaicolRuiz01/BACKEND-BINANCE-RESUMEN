package com.binance.web.BinanceAPI;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.Transacciones;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.SellDollars.SellDollarsDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.binance.web.model.Transaction;
import com.binance.web.transacciones.TransaccionesDTO;
import com.binance.web.transacciones.TransaccionesRepository;
import java.util.function.Function;



@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PaymentController {

	private static final String nameAccount = "account";

	@Autowired
	private BinanceService binanceService;

	@Autowired
	private AccountBinanceRepository accountBinanceRepository;

	@Autowired
	private BuyDollarsRepository buyDollarsRepository;

	@Autowired
	private SellDollarsRepository sellDollarsRepository;
	@Autowired
	private TransaccionesRepository transaccionesRepository;
	@Autowired
	private ClienteRepository clienteRepository;

	@GetMapping("/payments")
	public ResponseEntity<String> getPaymentHistory(@RequestParam(nameAccount) String account) {
		String response = binanceService.getPaymentHistory(account);
		return ResponseEntity.ok().body(response);
	}

	public List<Transaction> parseTransactions(String response) {
		// Asumiendo que la respuesta es un JSON, usa alguna librería como Jackson para
		// convertir el JSON en objetos de tipo Transaction
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			JsonNode rootNode = objectMapper.readTree(response);
			JsonNode dataNode = rootNode.path("data");

			// Verificamos si la propiedad 'data' está presente y contiene elementos
			if (dataNode.isArray() && dataNode.size() > 0) {
				List<Transaction> transactions = new ArrayList<>();
				for (JsonNode node : dataNode) {
					// Convertimos cada transacción del JSON a un objeto Transaction
					Transaction transaction = objectMapper.treeToValue(node, Transaction.class);
					transactions.add(transaction);
				}
				return transactions;
			} else {
				// Si no hay datos en la propiedad "data", devolvemos una lista vacía
				return new ArrayList<>();
			}
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Error parsing the transactions", e);
		}
	}
	
	@GetMapping("/compras-binancepay")
    public ResponseEntity<List<BuyDollarsDto>> getComprasNoRegistradas() {
        List<BuyDollarsDto> resultados = new ArrayList<>();

        try {
            LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

            Set<String> userBinanceValidos = accountBinanceRepository.findAll().stream()
                    .map(AccountBinance::getUserBinance)
                    .filter(nombre -> nombre != null && !nombre.isBlank())
                    .collect(Collectors.toSet());

            Set<String> idsRegistrados = buyDollarsRepository.findAll().stream()
                    .map(BuyDollars::getIdDeposit)
                    .collect(Collectors.toSet());

            for (String cuenta : binanceService.getAllAccountNames()) {
                String respuesta = binanceService.getPaymentHistory(cuenta);
                List<Transaction> transacciones = parseTransactions(respuesta);

                for (Transaction tx : transacciones) {
                    double monto = tx.getAmount();
                    LocalDateTime fecha = tx.getTransactionTime();
                    String cryptoSymbol = tx.getCurrency();

                    if (monto > 0 && !idsRegistrados.contains(tx.getOrderId())
                            && fecha != null && fecha.toLocalDate().isEqual(hoy)) {
                        if (tx.getPayerInfo() != null && !userBinanceValidos.contains(tx.getPayerInfo().getName())) {
                            BuyDollarsDto dto = new BuyDollarsDto();
                            dto.setIdDeposit(tx.getOrderId());
                            dto.setNameAccount(cuenta);
                            dto.setDate(fecha);
                            // ✅ Corregido: Usar 'amount' en lugar de 'dollars'
                            dto.setAmount(monto);
                            dto.setTasa(0.0);
                            dto.setPesos(0.0);
                            dto.setCryptoSymbol(cryptoSymbol);

                            resultados.add(dto);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
        return ResponseEntity.ok(resultados);
    }

	@GetMapping("/ventas-no-registradas-binancepay")
	public ResponseEntity<List<SellDollarsDto>> getVentasNoRegistradasBinancePay() {
	    List<SellDollarsDto> resultados = new ArrayList<>();

	    try {
	        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

	        Set<String> idsRegistrados = sellDollarsRepository.findAll().stream()
	                .map(SellDollars::getIdWithdrawals)
	                .collect(Collectors.toSet());

	        Set<String> userBinanceValidos = accountBinanceRepository.findAll().stream()
	                .map(AccountBinance::getUserBinance)
	                .filter(nombre -> nombre != null && !nombre.isBlank())
	                .collect(Collectors.toSet());

	     // 🔍 Mapa rápido para buscar clientes por binanceId
	        Map<Long, Cliente> clientePorBinanceId = clienteRepository.findAll().stream()
	            .filter(c -> c.getBinanceId() != null)
	            .collect(Collectors.toMap(Cliente::getBinanceId, Function.identity()));

	        for (String cuenta : binanceService.getAllAccountNames()) {
	            String respuesta = binanceService.getPaymentHistory(cuenta);
	            List<Transaction> transacciones = parseTransactions(respuesta);

	            for (Transaction tx : transacciones) {
	                double monto = tx.getAmount();
	                LocalDateTime fecha = tx.getTransactionTime();

	                if (monto < 0 && !idsRegistrados.contains(tx.getOrderId())
	                        && fecha != null && fecha.toLocalDate().isEqual(hoy)) {
	                    
	                	if (tx.getReceiverInfo() != null && !userBinanceValidos.contains(tx.getReceiverInfo().getName())) {
	                	    SellDollarsDto dto = new SellDollarsDto();
	                	    dto.setIdWithdrawals(tx.getOrderId());
	                	    dto.setNameAccount(cuenta);
	                	    dto.setDate(fecha);
	                	    dto.setDollars(Math.abs(monto));
	                	    dto.setTasa(0.0);
	                	    dto.setPesos(0.0);

	                	    // ✅ Usar binanceId del receiver para encontrar cliente
	                	    Long recvBinanceId = tx.getReceiverInfo().getBinanceId();
	                	    Cliente cliente = clientePorBinanceId.get(recvBinanceId);
	                	    if (cliente != null) {
	                	        dto.setClienteId(cliente.getId());
	                	    }

	                	    resultados.add(dto);
	                	}
	                }
	            }
	        }

	        return ResponseEntity.ok(resultados);

	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(500).body(new ArrayList<>());
	    }
	}


	@GetMapping("/transacciones-binacepay")
	public ResponseEntity<List<TransaccionesDTO>> getTransaccionesNoRegistradas() {
	    List<TransaccionesDTO> resultados = new ArrayList<>();

	    try {
	        // Usuarios válidos desde la base de datos
	        Set<String> userBinanceValidos = accountBinanceRepository.findAll().stream()
	                .map(AccountBinance::getUserBinance)
	                .filter(nombre -> nombre != null && !nombre.isBlank())
	                .collect(Collectors.toSet());

	        // Transacciones ya registradas
	        Set<String> idsRegistrados = transaccionesRepository.findAll().stream()
	                .map(Transacciones::getIdtransaccion)
	                .collect(Collectors.toSet());

	        for (String cuenta : binanceService.getAllAccountNames()) {
	            String respuesta = binanceService.getPaymentHistory(cuenta);
	            List<Transaction> transacciones = parseTransactions(respuesta);

	            for (Transaction tx : transacciones) {
	                double monto = tx.getAmount();

	                // Filtrar salidas de dinero no registradas con receptor válido
	                if (monto < 0 && !idsRegistrados.contains(tx.getOrderId())) {
	                    if (tx.getReceiverInfo() != null && userBinanceValidos.contains(tx.getReceiverInfo().getName())) {
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
	            }
	        }

	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(500).body(new ArrayList<>());
	    }

	    return ResponseEntity.ok(resultados);
	}
}
