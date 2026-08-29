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
import com.binance.web.dto.CuentaComprometidoDto;
import com.binance.web.service.AccountCopExcelService;
import com.binance.web.service.AccountCopService;
import com.binance.web.service.RetiradorService;

@RestController
@RequestMapping("/cuenta-cop")
@CrossOrigin("*")
public class AccountCopController {

	private final AccountCopService AccountCopService;
	private final AccountCopExcelService accountCopExcelService;
	private final BrebeKeyRepository brebeKeyRepository;
	private final RetiradorService retiradorService;
	private final com.binance.web.Repository.AccountCopRepository accountCopRepository;
	private final com.binance.web.Repository.MovimientoRepository movimientoRepository;
	private final com.binance.web.conciliacion.ConciliacionBancariaService conciliacionBancariaService;
	private final com.binance.web.activacion.CuentaP2PSyncService cuentaP2PSyncService;

	public AccountCopController(AccountCopService AccountCopService,
			AccountCopExcelService accountCopExcelService,
			BrebeKeyRepository brebeKeyRepository,
			RetiradorService retiradorService,
			com.binance.web.Repository.AccountCopRepository accountCopRepository,
			com.binance.web.Repository.MovimientoRepository movimientoRepository,
			com.binance.web.conciliacion.ConciliacionBancariaService conciliacionBancariaService,
			com.binance.web.activacion.CuentaP2PSyncService cuentaP2PSyncService) {
		this.AccountCopService = AccountCopService;
		this.accountCopExcelService = accountCopExcelService;
		this.brebeKeyRepository = brebeKeyRepository;
		this.retiradorService = retiradorService;
		this.accountCopRepository = accountCopRepository;
		this.movimientoRepository = movimientoRepository;
		this.conciliacionBancariaService = conciliacionBancariaService;
		this.cuentaP2PSyncService = cuentaP2PSyncService;
	}

	@GetMapping(produces = "application/json")
	public ResponseEntity<List<AccountCop>> getAllAccountCop() {
		List<AccountCop> cuentasCop = AccountCopService.findAllAccountCop();
		return ResponseEntity.ok(cuentasCop);
	}

	/**
	 * Total COP disponible — MISMO valor para la card "CUENTAS COP" y el label
	 * "TOTAL COP DISPONIBLE". Suma de saldos de TODAS las cuentas COP, menos el
	 * 4x1000 diferido pendiente (el de Bancolombia que se cobraría al día siguiente;
	 * para el total NO se difiere, se descuenta de una), y a ese neto se le resta el
	 * 4x1000 de sacarlo (× 0.996).
	 */
	@GetMapping(value = "/total-disponible", produces = "application/json")
	public ResponseEntity<Double> getTotalCopDisponible() {
		double sumaSaldos = accountCopRepository.sumBalances();
		double pendiente4x1000 = movimientoRepository.sumComisionPendienteBancolombia();
		double neto = sumaSaldos - pendiente4x1000;
		double disponible = neto * 0.996; // 4x1000 para sacarlo
		return ResponseEntity.ok(disponible);
	}

	/** Consulta liviana: solo id + saldo de cada cuenta COP. Rápida, para refrescar el saldo. */
	@GetMapping(value = "/saldos", produces = "application/json")
	public ResponseEntity<List<com.binance.web.Repository.AccountCopRepository.SaldoView>> getSaldos() {
		return ResponseEntity.ok(AccountCopService.findAllSaldos());
	}

	/** Consulta liviana para la vista P2P (ventas en curso): nombre, banco, saldo, cupos y estado P2P,
	 *  SIN las llaves Brebe. Mucho más rápida que traer la entidad completa. */
	@GetMapping(value = "/p2p", produces = "application/json")
	public ResponseEntity<List<com.binance.web.Repository.AccountCopRepository.P2PView>> getP2PView() {
		return ResponseEntity.ok(accountCopRepository.findAllP2PView());
	}

	/**
	 * Cuánto dinero de cada cuenta ya está "comprometido" en solicitudes de
	 * retiro enviadas pero aún no confirmadas por el retirador (SIN_ASIGNAR o
	 * PENDIENTE), con el desglose de esas solicitudes. Para mostrar en la
	 * vista de Cuentas: saldo bruto vs. saldo disponible.
	 */
	@GetMapping(value = "/comprometido", produces = "application/json")
	public ResponseEntity<List<CuentaComprometidoDto>> getMontosComprometidos() {
		return ResponseEntity.ok(retiradorService.obtenerMontosComprometidos());
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

	@org.springframework.beans.factory.annotation.Autowired
	private com.binance.web.Repository.BuyP2PRepository buyP2PRepository;

	/** Compras P2P asignadas a esta cuenta COP (para la vista de movimientos). */
	@GetMapping("/{id}/compras-p2p")
	public ResponseEntity<List<com.binance.web.model.CompraP2PCuentaDTO>> getComprasP2PByAccountCop(@PathVariable Integer id) {
		return ResponseEntity.ok(buyP2PRepository.findComprasP2PByAccountCop(id));
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

		// Una cuenta BLOQUEADA no puede activarse para P2P.
		if (Boolean.TRUE.equals(cuenta.getBloqueada())) {
			return ResponseEntity.badRequest().body(cuenta);
		}

		boolean estabaActivaAntes = Boolean.TRUE.equals(cuenta.getActivaParaP2P());
		boolean nuevoEstado = !estabaActivaAntes;
		cuenta.setActivaParaP2P(nuevoEstado);
		AccountCopService.updateAccountCop(id, cuenta);

		// Activado agosto 2026: avisa automáticamente a Movimientos (activación o
		// detención de monitoreo, según corresponda) cada vez que este botón cambia
		// el estado P2P de la cuenta. Ver CuentaP2PSyncService — mismo criterio
		// centralizado que usa la selección automática de Maicol
		// (activarCincoCuentasMasCercanasAlCupo). El riesgo de backlog que motivó
		// tenerlo desactivado ya se probó a mano con el botón de la lupa
		// (solicitarActivacionManual) sin problemas.
		cuentaP2PSyncService.sincronizar(cuenta, estabaActivaAntes);

		return ResponseEntity.ok(cuenta);
	}

	/**
	 * POST /cuenta-cop/{id}/solicitar-conciliacion
	 * Dispara manualmente el mismo aviso al bot de conciliación que dispara
	 * activar la cuenta en P2P (toggle-p2p), pero SIN tocar el estado de P2P —
	 * para poder probar la conexión con el bot en cualquier momento, sin
	 * desactivar/reactivar la cuenta. Pensado como botón de prueba, no para
	 * uso diario. Requiere login normal (esta ruta NO es la del bot).
	 */
	@PostMapping("/{id}/solicitar-conciliacion")
	public ResponseEntity<?> solicitarConciliacionManual(@PathVariable Integer id) {
		AccountCop cuenta = AccountCopService.findByIdAccountCop(id);
		if (cuenta == null) return ResponseEntity.notFound().build();

		if (cuenta.getBankType() != com.binance.web.Entity.BankType.BANCOLOMBIA) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "El bot de conciliación solo revisa cuentas Bancolombia."));
		}

		conciliacionBancariaService.solicitarConciliacion(cuenta);
		return ResponseEntity.ok(Map.of("ok", true, "cuenta", cuenta.getName()));
	}

	/**
	 * POST /cuenta-cop/{id}/solicitar-activacion
	 * Le pide al bot de Movimientos (iniciar.py / pochonance_activador.py)
	 * que arranque el MONITOREO CONTINUO de esta cuenta — no un chequeo
	 * puntual como solicitarConciliacionManual, sino la misma acción que ya
	 * dispara el botón "Comenzar monitoreo" del menú de Telegram. El propio
	 * bot reporta después, por separado (evento "conexion_exitosa" o
	 * "error_login" en /movimientos/evento), si la cuenta realmente sirve.
	 * No toca el estado de P2P.
	 *
	 * OJO (agosto 2026): esto llama a conciliacionBancariaService
	 * .solicitarConciliacion, NO a activacionService — pochonance_activador.py
	 * (el bot) escucha /conciliacion/pendiente, no /movimientos/activacion/
	 * pendiente. La cola activacion_solicitud quedó huérfana (nada la
	 * consume); se deja el servicio/endpoints por si se retoma más adelante,
	 * pero ningún camino nuevo debe apuntar ahí — ver CuentaP2PSyncService.
	 */
	@PostMapping("/{id}/solicitar-activacion")
	public ResponseEntity<?> solicitarActivacionManual(@PathVariable Integer id) {
		AccountCop cuenta = AccountCopService.findByIdAccountCop(id);
		if (cuenta == null) return ResponseEntity.notFound().build();

		if (cuenta.getBankType() != com.binance.web.Entity.BankType.BANCOLOMBIA) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", "El monitoreo automático solo existe para cuentas Bancolombia."));
		}

		conciliacionBancariaService.solicitarConciliacion(cuenta);
		return ResponseEntity.ok(Map.of("ok", true, "cuenta", cuenta.getName()));
	}

	/**
	 * PATCH /cuenta-cop/{id}/toggle-bloqueo
	 * Bloquea / desbloquea la cuenta. Bloqueada = no aparece ni es seleccionable en ningún
	 * lado (movimientos, formularios, P2P, ventas en curso, retiradores, gastos, pagos).
	 * Al bloquear, se desactiva de P2P automáticamente.
	 */
	@PatchMapping("/{id}/toggle-bloqueo")
	public ResponseEntity<AccountCop> toggleBloqueo(@PathVariable Integer id) {
		AccountCop cuenta = AccountCopService.findByIdAccountCop(id);
		if (cuenta == null) return ResponseEntity.notFound().build();

		boolean nuevoEstado = !Boolean.TRUE.equals(cuenta.getBloqueada());
		boolean estabaActivaAntes = Boolean.TRUE.equals(cuenta.getActivaParaP2P());
		cuenta.setBloqueada(nuevoEstado);
		if (nuevoEstado) {
			cuenta.setActivaParaP2P(false); // bloqueada no puede estar activa en P2P
		}
		AccountCopService.updateAccountCop(id, cuenta);
		cuentaP2PSyncService.sincronizar(cuenta, estabaActivaAntes);
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
