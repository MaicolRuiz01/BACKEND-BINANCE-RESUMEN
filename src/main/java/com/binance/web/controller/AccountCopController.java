package com.binance.web.controller;

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

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.service.AccountCopExcelService;
import com.binance.web.service.AccountCopService;

@RestController
@RequestMapping("/cuenta-cop")
public class AccountCopController {

	private final AccountCopService AccountCopService;
	private final AccountCopExcelService accountCopExcelService;

	public AccountCopController(AccountCopService AccountCopService, AccountCopExcelService accountCopExcelService) {
		this.AccountCopService = AccountCopService;
		this.accountCopExcelService = accountCopExcelService;
	}

	@GetMapping(produces = "application/json")
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
	
	@GetMapping("/{id}/sales")
    public ResponseEntity<List<SaleP2P>> getSalesByAccountCop(@PathVariable Integer id) {
        List<SaleP2P> sales = AccountCopService.getSalesByAccountCopId(id);
        return ResponseEntity.ok(sales);
    }
	
	@PostMapping("/accountCop/{id}/reconcile")
	public ResponseEntity<String> reconcile(@PathVariable Integer id){
	    return ResponseEntity.ok(AccountCopService.reconcileAccountCop(id));
	}
	
	@GetMapping(
	        value = "/excel/{cuentaId}",
	        produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
	    )
	    public ResponseEntity<byte[]> excelAccountCop(@PathVariable Integer cuentaId) throws Exception {

	        byte[] file = accountCopExcelService.exportAccountCop(cuentaId);
	        String filename = "cuenta_cop_" + cuentaId + "_reporte.xlsx";

	        return ResponseEntity.ok()
	                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
	                .body(file);
	    }

}
