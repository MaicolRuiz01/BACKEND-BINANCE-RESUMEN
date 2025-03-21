package com.binance.web.AccountCop;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cuenta-cop")
public class AccountCopController {

	private final AccountCopService AccountCopService;

	public AccountCopController(AccountCopService AccountCopService) {
		this.AccountCopService = AccountCopService;
	}

	@GetMapping
	public ResponseEntity<List<AccountCop>> getAllAccountCop() {
		List<AccountCop> cuentasCop = AccountCopService.findAllAccountCop();
		return ResponseEntity.ok(cuentasCop);
	}

	@GetMapping("/{id}")
	public ResponseEntity<AccountCop> getAccountCopById(@PathVariable Integer id) {
		AccountCop AccountCop = AccountCopService.findByIdAccountCop(id);
		return AccountCop != null ? ResponseEntity.ok(AccountCop) : ResponseEntity.notFound().build();
	}

	@PostMapping
	public ResponseEntity<AccountCop> createAccountCop(@RequestBody AccountCop AccountCop) {
		AccountCopService.saveAccountCop(AccountCop);
		return ResponseEntity.status(HttpStatus.CREATED).body(AccountCop);
	}

	@PutMapping("/{id}")
	public ResponseEntity<AccountCop> updateAccountCop(@PathVariable Integer id, @RequestBody AccountCop AccountCop) {
		AccountCop existingAccountCop = AccountCopService.findByIdAccountCop(id);
		if (existingAccountCop == null) {
			return ResponseEntity.notFound().build();
		}
		AccountCopService.updateAccountCop(id, AccountCop);
		return ResponseEntity.ok(AccountCop);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteAccountCop(@PathVariable Integer id) {
		AccountCopService.deleteAccountCop(id);
		return ResponseEntity.noContent().build();
	}
}
