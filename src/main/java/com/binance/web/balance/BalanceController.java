package com.binance.web.balance;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/balance")
public class BalanceController {

	private static final String DATE2 = "date";

	@Autowired
	private BalanceService balanceService;

	@GetMapping
	public ResponseEntity<List<BalanceDTO>> getAllBalances() {
		List<BalanceDTO> balances = balanceService.showBalances();
		return ResponseEntity.ok(balances);
	}

	@GetMapping("/live")
	public ResponseEntity<BalanceDTO> getLiveBalanceToday() {
		BalanceDTO dto = balanceService.showLiveBalanceToday();
		return ResponseEntity.ok(dto);
	}

	@PostMapping("/save")
	public ResponseEntity<Void> createBalance(
			@RequestParam(DATE2) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		balanceService.createBalance(date);
		return ResponseEntity.ok().build();
	}
}
