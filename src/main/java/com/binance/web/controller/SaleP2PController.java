package com.binance.web.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.model.AssignAccountDto;
import com.binance.web.model.SaleP2PDto;
import com.binance.web.service.SaleP2PService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/saleP2P")
public class SaleP2PController {

	@Autowired
	private SaleP2PService saleP2PService;
	@Autowired
	private AccountBinanceRepository accountBinanceRepository;

	@GetMapping
	public ResponseEntity<List<SaleP2PDto>> getAllSales() {
		List<SaleP2PDto> sales = saleP2PService.findAllSaleP2P();
		return ResponseEntity.ok(sales);
	}

	@GetMapping("/today")
	public ResponseEntity<List<SaleP2PDto>> getAllSalesToday(@RequestParam String account) {
		List<SaleP2PDto> sales = saleP2PService.getLastSaleP2pToday(account);
		return ResponseEntity.ok(sales);
	}

	@GetMapping("/all")
	public ResponseEntity<List<SaleP2PDto>> getAllP2P() {
		List<SaleP2PDto> sales = saleP2PService.findAllSaleP2P();
		return ResponseEntity.ok(sales);
	}

	@GetMapping("/{id}")
	public ResponseEntity<SaleP2P> getSaleById(@PathVariable Integer id) {
		SaleP2P saleP2P = saleP2PService.findByIdSaleP2P(id);
		return saleP2P != null ? ResponseEntity.ok(saleP2P) : ResponseEntity.notFound().build();
	}

	@PostMapping("/assign-account-cop")
	public ResponseEntity<String> assignAccountCop(@RequestParam Integer saleId,
			@RequestBody List<AssignAccountDto> accounts) {
		saleP2PService.processAssignAccountCop(saleId, accounts);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/{id}")
	public ResponseEntity<SaleP2P> updateSale(@PathVariable Integer id, @RequestBody SaleP2P saleP2P) {
		SaleP2P existingSale = saleP2PService.findByIdSaleP2P(id);
		if (existingSale == null) {
			return ResponseEntity.notFound().build();
		}
		saleP2PService.updateSaleP2P(id, saleP2P);
		return ResponseEntity.ok(saleP2P);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteSale(@PathVariable Integer id) {
		saleP2PService.deleteSaleP2P(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("today/all-binance")
	public ResponseEntity<List<SaleP2PDto>> getAllSalesTodayAllBinanceAccounts() {
		List<SaleP2PDto> result = new ArrayList<>();
		List<AccountBinance> binanceAccounts = accountBinanceRepository.findAll().stream()
				.filter(acc -> "BINANCE".equalsIgnoreCase(acc.getTipo())).collect(Collectors.toList());

		for (AccountBinance account : binanceAccounts) {
			result.addAll(saleP2PService.getLastSaleP2pToday(account.getName()));
		}

		return ResponseEntity.ok(result);
	}

	@GetMapping("/binance/range")
	public ResponseEntity<String> getP2PFromBinanceRange(@RequestParam String account,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		String json = saleP2PService.getAllP2PFromBinance(account, from, to);
		return ResponseEntity.ok(json);
	}

	@GetMapping("/today/no-asignadas")
	public ResponseEntity<List<SaleP2PDto>> getTodayNoAsignadas(@RequestParam String account) {
		return ResponseEntity.ok(saleP2PService.getTodayNoAsignadas(account));
	}

	@GetMapping("/today/no-asignadas/all-binance")
	public ResponseEntity<List<SaleP2PDto>> getTodayNoAsignadasAllBinance() {
		// metodo auxiliar por si hay una asignacion doble
		// saleP2PService.fixDuplicateAssignmentsAuto(70);

		return ResponseEntity.ok(saleP2PService.getTodayNoAsignadasAllAccounts());
	}

}
