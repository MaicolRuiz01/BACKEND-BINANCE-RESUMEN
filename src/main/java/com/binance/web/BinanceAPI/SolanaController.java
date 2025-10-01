package com.binance.web.BinanceAPI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.SellDollars.SellDollarsDto;
import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.SellDollars;
import com.binance.web.Entity.Cliente;
import com.binance.web.Repository.ClienteRepository;

@RestController
@RequestMapping("/solana")
@CrossOrigin(origins = "*")
public class SolanaController {

	@Autowired
	private SolscanService solscanService;
	@Autowired
	private AccountBinanceRepository accountBinanceRepository;
	@Autowired
	private BuyDollarsRepository buyDollarsRepository;
	@Autowired
	private SellDollarsRepository sellDollarsRepository;
	@Autowired
	private ClienteRepository clienteRepository;

	// 🔎 Todas las wallets SOLANA/PHANTOM registradas
	private List<AccountBinance> solanaAccounts() {
		return accountBinanceRepository.findAll().stream().filter(a -> {
			String t = a.getTipo() == null ? "" : a.getTipo().trim().toUpperCase();
			return "SOLANA".equals(t) || "PHANTOM".equals(t);
		}).filter(a -> a.getAddress() != null && !a.getAddress().isBlank()).toList();
	}

	/**
	 * ✅ Entradas (BUY) hoy en SOLANA: USDC/SOL u otros SPL que lleguen a nuestras
	 * wallets
	 */
	@GetMapping("/entradas")
	public ResponseEntity<List<BuyDollarsDto>> getSolanaIncomingTransfers() {
		Set<String> yaAsignadas = buyDollarsRepository.findAll().stream().map(BuyDollars::getIdDeposit)
				.filter(Objects::nonNull).collect(Collectors.toSet());

		List<BuyDollarsDto> out = new ArrayList<>();
		for (AccountBinance acc : solanaAccounts()) {
			out.addAll(solscanService.listIncomingToday(acc.getAddress(), acc.getName(), yaAsignadas));
		}
		return ResponseEntity.ok(out);
	}

	/** ✅ Salidas (SELL) hoy en SOLANA: USDC/SOL hacia afuera */
	@GetMapping("/salidas")
	public ResponseEntity<List<SellDollarsDto>> getSolanaOutgoingTransfers() {
		Set<String> yaAsignadas = sellDollarsRepository.findAll().stream().map(SellDollars::getIdWithdrawals)
				.filter(Objects::nonNull).collect(Collectors.toSet());

		Map<String, Cliente> clientePorWallet = clienteRepository.findAll().stream()
				.filter(c -> c.getWallet() != null && !c.getWallet().isBlank())
				.collect(Collectors.toMap(c -> c.getWallet().trim().toLowerCase(), c -> c));

		List<SellDollarsDto> out = new ArrayList<>();
		for (AccountBinance acc : solanaAccounts()) {
			out.addAll(
					solscanService.listOutgoingToday(acc.getAddress(), acc.getName(), yaAsignadas, clientePorWallet));
		}
		return ResponseEntity.ok(out);
	}

	@GetMapping("/transfers")
	public ResponseEntity<String> transfers(@RequestParam String address, @RequestParam(required = false) Integer page,
			@RequestParam(name = "pageSize", required = false) Integer pageSize,
			@RequestParam(required = false) String tokenType // all|SOL|SPL
	) {
		String raw = solscanService.getTransfersRaw(address, page, pageSize, tokenType);
		return ResponseEntity.ok(raw);
	}

}