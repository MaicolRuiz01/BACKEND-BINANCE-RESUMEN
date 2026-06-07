package com.binance.web.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import com.binance.web.Entity.AccountBinance;

import com.binance.web.model.AccountBinanceDTO;
import com.binance.web.model.CryptoBalanceDto;
import com.binance.web.service.AccountBinanceService;

@Slf4j
@RestController
@RequestMapping("/cuenta-binance")
public class AccountBinanceController {

	private final AccountBinanceService accountBinanceService;

	public AccountBinanceController(AccountBinanceService accountBinanceService) {
		this.accountBinanceService = accountBinanceService;
	}

	@GetMapping
	public ResponseEntity<List<AccountBinanceDTO>> getAllAccounts() {
		List<AccountBinanceDTO> out = accountBinanceService.findAllAccountBinance().stream().map(this::toDtoConBalance)
				.toList();
		return ResponseEntity.ok(out);
	}

	// ==== OBTENER POR ID (también en DTO para consistencia) ====
	@GetMapping("/{id}")
	public ResponseEntity<AccountBinanceDTO> getAccountById(@PathVariable Integer id) {
		AccountBinance account = accountBinanceService.findByIdAccountBinance(id);
		if (account == null)
			return ResponseEntity.notFound().build();
		return ResponseEntity.ok(toDtoConBalance(account));
	}

	@PostMapping
	public ResponseEntity<?> createAccount(@RequestBody AccountBinance accountBinance) {
		try {
			accountBinanceService.saveAccountBinance(accountBinance);
			return ResponseEntity.status(HttpStatus.CREATED).body(accountBinance);
		} catch (Exception e) {
			log.error("[AccountBinance] Error al crear cuenta '{}': {}", accountBinance.getName(), e.getMessage(), e);
			String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("message", msg != null ? msg : "Error interno al guardar la cuenta"));
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<AccountBinance> updateAccount(@PathVariable Integer id,
			@RequestBody AccountBinance accountBinance) {
		AccountBinance existing = accountBinanceService.findByIdAccountBinance(id);
		if (existing == null) {
			return ResponseEntity.notFound().build();
		}
		accountBinanceService.updateAccountBinance(id, accountBinance);
		return ResponseEntity.ok(accountBinance);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteAccount(@PathVariable Integer id) {
		accountBinanceService.deleteAccountBinance(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/buscar")
	public ResponseEntity<AccountBinance> getAccountByName(@RequestParam String name) {
		AccountBinance account = accountBinanceService.findByName(name);
		return account != null ? ResponseEntity.ok(account) : ResponseEntity.notFound().build();
	}

	// Balance interno (según tu tabla) convertido a USD
	@GetMapping("/balance")
	public ResponseEntity<Double> getBalanceByName(@RequestParam String name) {
		Double usd = accountBinanceService.getInternalUsdBalance(name);
		return ResponseEntity.ok(usd != null ? usd : 0.0);
	}

	// Balance EXTERNO en USDT (lo que ya calculabas con Binance/Tron) → asegurado
	// Double
	@GetMapping("/balance-usdt")
	public ResponseEntity<Double> getUSDTBalance(@RequestParam String name) {
		Double usdtBalance = accountBinanceService.getUSDTBalance(name);
		return ResponseEntity.ok(usdtBalance != null ? usdtBalance : 0.0);
	}

	@GetMapping("/balance-total-externo")
	public Double obtenerBalanceTotalExterno() {
		return accountBinanceService.getTotalExternalBalance();
	}

	// este me da todos los dalos de la cuetnas binance pasado a pesos
	@GetMapping("/total-balance")
	public ResponseEntity<BigDecimal> getTotalBalanceMultiplied() {
		BigDecimal result = accountBinanceService.getTotalBalance();
		return ResponseEntity.ok(result);
	}

	@GetMapping("/total-balance-interno")
	public ResponseEntity<BigDecimal> getTotalBalanceInterno() {
		BigDecimal result = accountBinanceService.getTotalBalanceInterno();
		return ResponseEntity.ok(result);
	}

	@PostMapping("/sync-internal")
	public ResponseEntity<Map<String, Double>> syncInternal(@RequestParam String name) {
		// Sincroniza interno desde snapshot externo (soporta BINANCE y TRUST)
		Map<String, Double> snapshot = accountBinanceService.syncInternalBalancesFromExternal(name);
		return ResponseEntity.ok(snapshot);
	}

	@PostMapping("/sync-internal/all")
	public ResponseEntity<String> syncAllInternal() {
		accountBinanceService.syncAllInternalBalancesFromExternal();
		return ResponseEntity.ok("Sincronización interna completada para todas las cuentas.");
	}

	private AccountBinanceDTO toDtoConBalance(AccountBinance a) {
		Double balanceUsd = 0.0;
		try {
			balanceUsd = accountBinanceService.getInternalUsdBalance(a.getName());
		} catch (Exception ignored) {
		}
		return AccountBinanceDTO.builder().id(a.getId()).name(a.getName()).referenceAccount(a.getReferenceAccount())
				.correo(a.getCorreo()).userBinance(a.getUserBinance()).address(a.getAddress()).tipo(a.getTipo())
				.balance(balanceUsd != null ? balanceUsd : 0.0).build();
	}
	
	@GetMapping("/balances-internos")
	public ResponseEntity<List<CryptoBalanceDto>> getInternalBalances(@RequestParam String name) {
	    List<CryptoBalanceDto> list = accountBinanceService.getInternalBalancesDetail(name);
	    return ResponseEntity.ok(list);
	}


}
