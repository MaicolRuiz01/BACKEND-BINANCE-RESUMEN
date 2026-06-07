package com.binance.web.BinanceAPI;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.Cliente;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.model.BuyDollarsDto;
import com.binance.web.model.SellDollarsDto;
import com.binance.web.transacciones.TransaccionesDTO;
import com.binance.web.transacciones.TransaccionesRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TronScanController {

	@Autowired private TronScanService tronScanService;
	@Autowired private BuyDollarsRepository buyDollarsRepository;
	@Autowired private SellDollarsRepository sellDollarsRepository;
	@Autowired private TransaccionesRepository transaccionesRepository;
	@Autowired private AccountBinanceRepository accountBinanceRepository;
	@Autowired private ClienteRepository clienteRepository;

	/** Lista todas las wallets TRUST registradas */
	@GetMapping
	private List<String> getAllTrustWallets() {
		return accountBinanceRepository.findTrustAddresses();
	}

	/** Entradas nativas TRX (NO TRC20) */
	@GetMapping("/trx-entradas")
	public ResponseEntity<List<BuyDollarsDto>> getTrustTransactions() {
		// Wallets TRUST con nombre "TRUST_IN" o similar — se toman de BD, no hardcodeadas
		List<String> trustAddresses = accountBinanceRepository.findTrustAddresses();
		if (trustAddresses.isEmpty()) {
			return ResponseEntity.ok(List.of());
		}
		String walletAddress = trustAddresses.get(0); // primera wallet TRUST como principal de entrada

		Set<String> assignedIds = buyDollarsRepository.findAllDepositIds();

		String response = tronScanService.getTransactions(walletAddress);
		List<BuyDollarsDto> transactions = tronScanService.parseIncomingTransactions(response, walletAddress, assignedIds);

		return ResponseEntity.ok(transactions);
	}

	/** Salidas nativas TRX (uso informativo) */
	@GetMapping("/trx-salidas")
	public ResponseEntity<List<BuyDollarsDto>> getTrustOutgoingTransactions() {
		List<String> trustAddresses = accountBinanceRepository.findTrustAddresses();
		if (trustAddresses.isEmpty()) {
			return ResponseEntity.ok(List.of());
		}
		// segunda wallet si existe, si no la primera
		String walletAddress = trustAddresses.size() > 1 ? trustAddresses.get(1) : trustAddresses.get(0);

		Set<String> assignedIds = buyDollarsRepository.findAllDepositIds();

		String response = tronScanService.getTransactions(walletAddress);
		List<BuyDollarsDto> outgoingTransactions = tronScanService.parseOutgoingTransactions(response, walletAddress, assignedIds);

		return ResponseEntity.ok(outgoingTransactions);
	}

	/** Crudo TRC20 desde TronGrid (debug/inspección) */
	@GetMapping("/usdt-trc20-trongrid")
	public ResponseEntity<String> getTRC20TransfersUsingTronGrid(@RequestParam(required = false) String address) {
		List<String> trustAddresses = accountBinanceRepository.findTrustAddresses();
		String walletAddress = (address != null && !address.isBlank())
				? address
				: (trustAddresses.isEmpty() ? "" : trustAddresses.get(0));
		if (walletAddress.isEmpty()) {
			return ResponseEntity.badRequest().body("{\"error\": \"No hay wallets TRUST registradas\"}");
		}
		String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
		return ResponseEntity.ok(response);
	}

	/** Entradas TRC20 multi-cripto para todas las wallets TRUST */
	@GetMapping("/usdt-entradas")
	public ResponseEntity<List<BuyDollarsDto>> getUSDTIncomingTransfers() {
		Set<String> assignedIds = buyDollarsRepository.findAllDepositIds();

		List<com.binance.web.Entity.AccountBinance> trustWallets = accountBinanceRepository.findByTipo("TRUST").stream()
				.filter(a -> a.getAddress() != null && !a.getAddress().isBlank())
				.collect(Collectors.toList());

		List<BuyDollarsDto> result = new ArrayList<>();
		for (var trustAccount : trustWallets) {
			String walletAddress = trustAccount.getAddress();
			String accountName = trustAccount.getName();
			String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
			result.addAll(tronScanService.parseTRC20IncomingTransfers(response, walletAddress, accountName, assignedIds));
		}

		return ResponseEntity.ok(result);
	}

	/** Salidas TRC20 multi-cripto + comisión en TRX */
	@GetMapping("/usdt-salidas")
	public ResponseEntity<List<SellDollarsDto>> getUSDTOutgoingTransfers() {
		Set<String> assignedIds = sellDollarsRepository.findAllWithdrawalIds();

		Map<String, Cliente> clientePorWallet = clienteRepository.findByWalletNotNull().stream()
				.collect(Collectors.toMap(c -> c.getWallet().trim().toLowerCase(), c -> c, (a, b) -> a));

		List<com.binance.web.Entity.AccountBinance> trustWallets = accountBinanceRepository.findByTipo("TRUST").stream()
				.filter(a -> a.getAddress() != null && !a.getAddress().isBlank())
				.collect(Collectors.toList());

		List<SellDollarsDto> result = new ArrayList<>();
		for (var trustAccount : trustWallets) {
			String walletAddress = trustAccount.getAddress();
			String accountName = trustAccount.getName();
			String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
			result.addAll(tronScanService.parseTRC20OutgoingTransfers(response, walletAddress, accountName, assignedIds, clientePorWallet));
		}

		return ResponseEntity.ok(result);
	}

	/** Traspasos internos TRC20 entre wallets propias */
	@GetMapping("/trust-transacciones-salientes")
	public ResponseEntity<List<TransaccionesDTO>> getTrustOutgoingTransfers() {
		Set<String> registeredIds      = transaccionesRepository.findAllTransaccionIds();
		Set<String> registeredAddresses = accountBinanceRepository.findAllAddresses();
		List<String> trustWallets       = accountBinanceRepository.findTrustAddresses();

		List<TransaccionesDTO> result = new ArrayList<>();
		ObjectMapper mapper = new ObjectMapper();

		for (String walletAddress : trustWallets) {
			try {
				String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
				JsonNode data = mapper.readTree(response).path("data");

				if (!data.isArray()) continue;

				for (JsonNode tx : data) {
					String from   = tx.path("from").asText();
					String to     = tx.path("to").asText();
					String txId   = tx.path("transaction_id").asText();
					String symbol = tx.path("token_info").path("symbol").asText();

					if (!from.equalsIgnoreCase(walletAddress)) continue;
					if (!"USDT".equalsIgnoreCase(symbol)) continue;
					if (registeredIds.contains(txId)) continue;
					if (!registeredAddresses.contains(to)) continue;

					int decimals = 6;
					try { decimals = Integer.parseInt(tx.path("token_info").path("decimals").asText("6")); } catch (Exception ignore) {}
					double amount = Double.parseDouble(tx.path("value").asText("0")) / Math.pow(10, decimals);
					long timestamp = tx.path("block_timestamp").asLong();

					TransaccionesDTO dto = new TransaccionesDTO();
					dto.setIdtransaccion(txId);
					dto.setCuentaFrom(walletAddress);
					dto.setCuentaTo(to);
					dto.setFecha(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("America/Bogota")));
					dto.setMonto(amount);
					dto.setTipo("TRUST");
					result.add(dto);
				}
			} catch (Exception e) {
				log.error("Error procesando traspasos TRUST para wallet {}: {}", walletAddress, e.getMessage());
			}
		}

		return ResponseEntity.ok(result);
	}

	/** Total assets de la wallet (USD) */
	@GetMapping("/wallet-total-assets")
	public ResponseEntity<Double> getWalletTotalAssets(@RequestParam String walletAddress) {
		double totalUsd = tronScanService.getTotalAssetTokenOverview(walletAddress);
		return ResponseEntity.ok(totalUsd);
	}

	/** Movimientos unificados TRC20 + TRX nativos en un solo array ordenado */
	@GetMapping("/trongrid/movements-flat")
	public ResponseEntity<String> unifiedMovements(
			@RequestParam String address,
			@RequestParam(defaultValue = "200") int limit) {
		String json = tronScanService.getUnifiedMovementsJson(address, limit);
		return ResponseEntity.ok()
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.body(json);
	}

}
