package com.binance.web.BinanceAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Transacciones;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.SellDollars.SellDollarsDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.binance.web.model.Transaction;
import com.binance.web.transacciones.TransaccionesDTO;
import com.binance.web.transacciones.TransaccionesRepository;



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
	        // Obtener los nombres de usuario Binance válidos
	        Set<String> userBinanceValidos = accountBinanceRepository.findAll().stream()
	                .map(AccountBinance::getUserBinance)
	                .filter(nombre -> nombre != null && !nombre.isBlank())
	                .collect(Collectors.toSet());

	        // Obtener los IDs de depósitos ya registrados
	        Set<String> idsRegistrados = buyDollarsRepository.findAll().stream()
	                .map(BuyDollars::getIdDeposit)
	                .collect(Collectors.toSet());

	        // Iterar sobre todas las cuentas Binance
	        for (String cuenta : binanceService.getAllAccountNames()) {
	            String respuesta = binanceService.getPaymentHistory(cuenta);
	            List<Transaction> transacciones = parseTransactions(respuesta);

	            for (Transaction tx : transacciones) {
	                double monto = tx.getAmount();

	                // Filtrar entradas de dinero no registradas y no internas
	                if (monto > 0 && !idsRegistrados.contains(tx.getOrderId())) {
	                    if (tx.getPayerInfo() != null && !userBinanceValidos.contains(tx.getPayerInfo().getName())) {
	                        BuyDollarsDto dto = new BuyDollarsDto();
	                        dto.setIdDeposit(tx.getOrderId());
	                        dto.setNameAccount(cuenta);
	                        dto.setDate(tx.getTransactionTime());
	                        dto.setDollars(monto);
	                        dto.setTasa(0.0);
	                        dto.setPesos(0.0);

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
	        // Obtener IDs de ventas ya registradas
	        Set<String> idsRegistrados = sellDollarsRepository.findAll().stream()
	                .map(sell -> sell.getIdWithdrawals())
	                .collect(Collectors.toSet());
	        
	        Set<String> userBinanceValidos = accountBinanceRepository.findAll().stream()
	                .map(AccountBinance::getUserBinance)
	                .filter(nombre -> nombre != null && !nombre.isBlank())
	                .collect(Collectors.toSet());


	        for (String cuenta : binanceService.getAllAccountNames()) {
	            String respuesta = binanceService.getPaymentHistory(cuenta);
	            List<Transaction> transacciones = parseTransactions(respuesta);

	            for (Transaction tx : transacciones) {
	                double monto = tx.getAmount();

	                // Solo salidas de dinero no registradas
	                if (monto < 0 && !idsRegistrados.contains(tx.getOrderId())) {
	                	 if (tx.getReceiverInfo() != null && !userBinanceValidos.contains(tx.getReceiverInfo().getName())) {
	                    SellDollarsDto dto = new SellDollarsDto();
	                    dto.setIdWithdrawals(tx.getOrderId());
	                    dto.setNameAccount(cuenta);
	                    dto.setDate(tx.getTransactionTime());
	                    dto.setDollars(Math.abs(monto));
	                    dto.setTasa(0.0);
	                    dto.setPesos(0.0);
	                    

	                    resultados.add(dto);
	                }}
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
