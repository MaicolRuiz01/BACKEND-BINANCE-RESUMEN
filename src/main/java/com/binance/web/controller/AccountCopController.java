package com.binance.web.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BrebeKey;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Repository.BrebeKeyRepository;
import com.binance.web.service.AccountCopExcelService;
import com.binance.web.service.AccountCopService;

@RestController
@RequestMapping("/cuenta-cop")
@CrossOrigin("*")
public class AccountCopController {

	private final AccountCopService AccountCopService;
	private final AccountCopExcelService accountCopExcelService;
	private final BrebeKeyRepository brebeKeyRepository;

	public AccountCopController(AccountCopService AccountCopService,
			AccountCopExcelService accountCopExcelService,
			BrebeKeyRepository brebeKeyRepository) {
		this.AccountCopService = AccountCopService;
		this.accountCopExcelService = accountCopExcelService;
		this.brebeKeyRepository = brebeKeyRepository;
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
	public ResponseEntity<?> createAccountCop(@RequestBody AccountCop AccountCop) {
		try {
			AccountCopService.saveAccountCop(AccountCop);
			return ResponseEntity.status(HttpStatus.CREATED).body(AccountCop);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(Map.of("message", e.getMessage()));
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> updateAccountCop(@PathVariable Integer id, @RequestBody AccountCop AccountCop) {
		AccountCop existingAccountCop = AccountCopService.findByIdAccountCop(id);
		if (existingAccountCop == null) {
			return ResponseEntity.notFound().build();
		}
		try {
			AccountCopService.updateAccountCop(id, AccountCop);
			return ResponseEntity.ok(AccountCop);
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(Map.of("message", e.getMessage()));
		}
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
	
	/**
	 * PATCH /cuenta-cop/{id}/toggle-p2p
	 * Alterna el flag activaParaP2P de la cuenta (true → false → true).
	 * Retorna la cuenta actualizada.
	 */
	@PatchMapping("/{id}/toggle-p2p")
	public ResponseEntity<AccountCop> toggleActivaParaP2P(@PathVariable Integer id) {
		AccountCop cuenta = AccountCopService.findByIdAccountCop(id);
		if (cuenta == null) return ResponseEntity.notFound().build();

		boolean nuevoEstado = !Boolean.TRUE.equals(cuenta.getActivaParaP2P());
		cuenta.setActivaParaP2P(nuevoEstado);
		AccountCopService.updateAccountCop(id, cuenta);
		return ResponseEntity.ok(cuenta);
	}

	/**
	 * PATCH /cuenta-cop/{id}/cupo-tipo
	 * Cambia el tipo de cupo que se respeta al asignar esta cuenta en P2P.
	 * Body: { "cupoTipoP2P": "CAJERO" | "CORRESPONSAL" | "AMBOS" }
	 */
	@PatchMapping("/{id}/cupo-tipo")
	public ResponseEntity<?> setCupoTipoP2P(@PathVariable Integer id, @RequestBody Map<String, String> body) {
		AccountCop cuenta = AccountCopService.findByIdAccountCop(id);
		if (cuenta == null) return ResponseEntity.notFound().build();
		String tipo = body.get("cupoTipoP2P");
		if (!Set.of("CAJERO", "CORRESPONSAL", "AMBOS").contains(tipo)) {
			return ResponseEntity.badRequest().body(Map.of("error", "cupoTipoP2P debe ser CAJERO, CORRESPONSAL o AMBOS"));
		}
		cuenta.setCupoTipoP2P(tipo);
		AccountCopService.updateAccountCop(id, cuenta);
		return ResponseEntity.ok(cuenta);
	}

	// ══════════════════════════════════════════════════════════════
	// LLAVES BREBE
	// ══════════════════════════════════════════════════════════════

	/**
	 * POST /cuenta-cop/{id}/brebe-keys
	 * Agrega una nueva llave Brebe a la cuenta.
	 * Body: { "llave": "...", "descripcion": "..." }
	 */
	@PostMapping("/{id}/brebe-keys")
	public ResponseEntity<?> addBrebeKey(@PathVariable Integer id, @RequestBody Map<String, String> body) {
		AccountCop cuenta = AccountCopService.findByIdAccountCop(id);
		if (cuenta == null) return ResponseEntity.notFound().build();

		String llave = body.get("llave");
		if (llave == null || llave.trim().isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("error", "El campo 'llave' es requerido"));
		}

		BrebeKey key = new BrebeKey();
		key.setLlave(llave.trim());
		String desc = body.get("descripcion");
		if (desc != null && !desc.trim().isEmpty()) key.setDescripcion(desc.trim());
		key.setAccountCop(cuenta);
		brebeKeyRepository.save(key);

		return ResponseEntity.ok(key);
	}

	/**
	 * DELETE /cuenta-cop/{id}/brebe-keys/{keyId}
	 * Elimina una llave Brebe de la cuenta.
	 * Verifica pertenencia via query para evitar lazy-load fuera de transacción.
	 */
	@DeleteMapping("/{id}/brebe-keys/{keyId}")
	public ResponseEntity<Void> deleteBrebeKey(@PathVariable Integer id, @PathVariable Integer keyId) {
		boolean belongs = brebeKeyRepository.findByAccountCopId(id)
				.stream().anyMatch(k -> k.getId().equals(keyId));
		if (!belongs) return ResponseEntity.notFound().build();
		brebeKeyRepository.deleteById(keyId);
		return ResponseEntity.noContent().build();
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
