package com.binance.web.BinanceAPI;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
import com.binance.web.transacciones.TransaccionesDTO;
import com.binance.web.transacciones.TransaccionesRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TronScanController {

	@Autowired
	private TronScanService tronScanService;

	@Autowired
	private BuyDollarsRepository buyDollarsRepository;

	@Autowired
	private SellDollarsRepository sellDollarsRepository;

	@Autowired
	private TransaccionesRepository transaccionesRepository;

	@Autowired
	private AccountBinanceRepository accountBinanceRepository;

	@Autowired
	private ClienteRepository clienteRepository;

	private List<String> getAllTrustWallets() {
		return accountBinanceRepository.findAll().stream()
				.filter(account -> "TRUST".equalsIgnoreCase(account.getTipo())).map(AccountBinance::getAddress)
				.filter(address -> address != null && !address.trim().isEmpty()).collect(Collectors.toList());
	}

	// Entradas nativas TRX (NO TRC20)
	@GetMapping("/trx-entradas")
	public ResponseEntity<List<BuyDollarsDto>> getTrustTransactions() {
		String walletAddress = "TJjK97sE4anv35hBQGwGEZEo5FYxToxFQM";

		Set<String> assignedIds = buyDollarsRepository.findAll().stream().map(BuyDollars::getIdDeposit)
				.collect(Collectors.toSet());

		String response = tronScanService.getTransactions(walletAddress);
		List<BuyDollarsDto> transactions = tronScanService.parseIncomingTransactions(response, walletAddress,
				assignedIds);

		return ResponseEntity.ok(transactions);
	}

	// Salidas nativas TRX (uso informativo)
	@GetMapping("/trx-salidas")
	public ResponseEntity<List<BuyDollarsDto>> getTrustOutgoingTransactions() {
		String walletAddress = "TJjK97sE4anv35hBQGwGEZEo5FYxToxFQM";

		Set<String> assignedIds = buyDollarsRepository.findAll().stream().map(BuyDollars::getIdDeposit)
				.collect(Collectors.toSet());

		String response = tronScanService.getTransactions(walletAddress);
		List<BuyDollarsDto> outgoingTransactions = tronScanService.parseOutgoingTransactions(response, walletAddress,
				assignedIds);

		return ResponseEntity.ok(outgoingTransactions);
	}

	// Crudo TRC20 desde TronGrid (debug/inspección)
	@GetMapping("/usdt-trc20-trongrid")
	public ResponseEntity<String> getTRC20TransfersUsingTronGrid() {
		String walletAddress = "TPDNfJ72Fh6Hrfk6faYVps1rN78NB8LQGu";
		String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
		return ResponseEntity.ok(response);
	}

	// Entradas TRC20 multi-cripto (antes: solo USDT)
	@GetMapping("/usdt-entradas")
	public ResponseEntity<List<BuyDollarsDto>> getUSDTIncomingTransfers() {
		Set<String> assignedIds = buyDollarsRepository.findAll().stream().map(BuyDollars::getIdDeposit)
				.collect(Collectors.toSet());

		List<AccountBinance> trustWallets = accountBinanceRepository.findAll().stream()
				.filter(account -> "TRUST".equalsIgnoreCase(account.getTipo()))
				.filter(account -> account.getAddress() != null && !account.getAddress().trim().isEmpty())
				.collect(Collectors.toList());

		List<BuyDollarsDto> result = new ArrayList<>();

		for (AccountBinance trustAccount : trustWallets) {
			String walletAddress = trustAccount.getAddress();
			String accountName = trustAccount.getName();
			String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);

			// ⬇️ IMPORTANTE: usa el parser multi-cripto
			List<BuyDollarsDto> incoming = tronScanService.parseTRC20IncomingTransfers(response, walletAddress,
					accountName, assignedIds);

			result.addAll(incoming);
		}

		return ResponseEntity.ok(result);
	}

	// Salidas TRC20 multi-cripto + comisión en TRX
	@GetMapping("/usdt-salidas")
	public ResponseEntity<List<SellDollarsDto>> getUSDTOutgoingTransfers() {
		Set<String> assignedIds = sellDollarsRepository.findAll().stream().map(SellDollars::getIdWithdrawals)
				.collect(Collectors.toSet());

		Map<String, Cliente> clientePorWallet = clienteRepository.findAll().stream()
				.filter(c -> c.getWallet() != null && !c.getWallet().trim().isEmpty())
				.collect(Collectors.toMap(c -> c.getWallet().trim().toLowerCase(), c -> c));

		List<AccountBinance> trustWallets = accountBinanceRepository.findAll().stream()
				.filter(account -> "TRUST".equalsIgnoreCase(account.getTipo()))
				.filter(account -> account.getAddress() != null && !account.getAddress().trim().isEmpty())
				.collect(Collectors.toList());

		List<SellDollarsDto> result = new ArrayList<>();

		for (AccountBinance trustAccount : trustWallets) {
			String walletAddress = trustAccount.getAddress();
			String accountName = trustAccount.getName();
			String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);

			// ⬇️ IMPORTANTE: usa el parser multi-cripto renombrado
			List<SellDollarsDto> outgoing = tronScanService.parseTRC20OutgoingTransfers(response, walletAddress,
					accountName, assignedIds, clientePorWallet);

			result.addAll(outgoing);
		}

		return ResponseEntity.ok(result);
	}

	// Traspasos internos TRC20 (este endpoint sigue filtrando USDT; puedes
	// generalizarlo si quieres)
	@GetMapping("/trust-transacciones-salientes")
	public ResponseEntity<List<TransaccionesDTO>> getTrustOutgoingTransfers() {

		Set<String> registeredIds = transaccionesRepository.findAll().stream().map(Transacciones::getIdtransaccion)
				.collect(Collectors.toSet());

		Set<String> registeredAddresses = accountBinanceRepository.findAll().stream().map(AccountBinance::getAddress)
				.filter(address -> address != null && !address.trim().isEmpty()).collect(Collectors.toSet());

		List<String> trustWallets = accountBinanceRepository.findAll().stream()
				.filter(a -> "TRUST".equalsIgnoreCase(a.getTipo())).map(AccountBinance::getAddress)
				.filter(address -> address != null && !address.trim().isEmpty()).collect(Collectors.toList());

		List<TransaccionesDTO> result = new ArrayList<>();

		for (String walletAddress : trustWallets) {
			try {
				String response = tronScanService.getTRC20TransfersUsingTronGrid(walletAddress);
				JsonNode root = new ObjectMapper().readTree(response);
				JsonNode data = root.path("data");

				if (data.isArray()) {
					for (JsonNode tx : data) {
						String from = tx.path("from").asText();
						String to = tx.path("to").asText();
						String txId = tx.path("transaction_id").asText();
						JsonNode tokenInfo = tx.path("token_info");
						String symbol = tokenInfo.path("symbol").asText();

						if (from.equalsIgnoreCase(walletAddress) && symbol.equalsIgnoreCase("USDT") // <- puedes hacerlo
																									// multi-cripto si
																									// quieres
								&& !registeredIds.contains(txId) && registeredAddresses.contains(to)) {

							double amount = Double.parseDouble(tx.path("value").asText("0")) / 1_000_000.0;
							long timestamp = tx.path("block_timestamp").asLong();

							TransaccionesDTO dto = new TransaccionesDTO();
							dto.setIdtransaccion(txId);
							dto.setCuentaFrom(walletAddress);
							dto.setCuentaTo(to);
							dto.setFecha(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp),
									ZoneId.of("America/Bogota")));
							dto.setMonto(amount);
							dto.setTipo("TRUST");

							result.add(dto);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return ResponseEntity.ok(result);
	}

	// Total assets de la wallet (USD)
	@GetMapping("/wallet-total-assets")
	public ResponseEntity<Double> getWalletTotalAssets(@RequestParam String walletAddress) {
		double totalUsd = tronScanService.getTotalAssetTokenOverview(walletAddress);
		return ResponseEntity.ok(totalUsd);
	}
}
