package com.binance.web.cryptoAverageRate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.AccountBinance.AccountBinanceService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.CryptoAverageRate;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.model.CryptoPendienteDto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

//package com.binance.web.controller;  <- ajusta a tu paquete real

@RestController
@RequestMapping("/api/crypto-average-rate")
@RequiredArgsConstructor
public class CryptoAverageRateController {

	private final CryptoAverageRateService cryptoAverageRateService;
	private final AccountBinanceRepository accountBinanceRepository;
	private final AccountBinanceService accountBinanceService;
	private final CryptoAverageRateService service;

	// DTO simple para request de inicialización
	@Data
	public static class InicializarCriptoRequest {
		private String cripto; // "TRX", "BNB"
		private Double tasaInicialUsdt; // precio en USDT
	}

	/**
	 * Última tasa promedio registrada para una cripto (puede devolver 204 si no
	 * hay)
	 */
	@GetMapping("/ultima")
	public ResponseEntity<CryptoAverageRate> getUltima(@RequestParam String cripto) {
		CryptoAverageRate rate = cryptoAverageRateService.getUltimaPorCripto(cripto);
		if (rate == null) {
			return ResponseEntity.noContent().build(); // 204
		}
		return ResponseEntity.ok(rate);
	}

	/** Inicializa la tasa promedio de una cripto (usa saldo externo real) */
	@PostMapping("/inicializar")
	public ResponseEntity<CryptoAverageRate> inicializar(@RequestBody InicializarCriptoRequest req) {
	    if (req.getCripto() == null) {
	        return ResponseEntity.badRequest().build();
	    }

	    CryptoAverageRate rate = cryptoAverageRateService.inicializarCripto(
	            req.getCripto(),
	            req.getTasaInicialUsdt(), // 👈 puede venir null
	            LocalDateTime.now()
	    );

	    return ResponseEntity.status(HttpStatus.CREATED).body(rate);
	}

	@GetMapping("/crypto-con-balance")
	public ResponseEntity<List<String>> listarCriptosConBalanceExterno() {
	    Set<String> out = new HashSet<>();

	    for (AccountBinance acc : accountBinanceRepository.findAll()) {
	        try {
	            Map<String, Double> snap = accountBinanceService.getExternalBalancesSnapshot(acc.getName());
	            snap.forEach((sym, qty) -> {
	                if (qty != null && qty > 0.00001) out.add(sym.toUpperCase());
	            });
	        } catch (Exception ignored) {}
	    }

	    return ResponseEntity.ok(out.stream().sorted().toList());
	}
	
	@GetMapping("/pendientes")
    public ResponseEntity<List<CryptoPendienteDto>> listarPendientes() {
        return ResponseEntity.ok(service.listarCriptosPendientesInicializacion());
    }
	
	@GetMapping("/del-dia")
	public ResponseEntity<List<CryptoAverageRate>> listarDelDia() {
	    LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
	    return ResponseEntity.ok(service.listarPorDia(hoy));
	}
	
	@PostMapping("/init-dia")
    public ResponseEntity<Void> inicializarDia() {
        // 1) sincroniza interno desde externo (todas las cuentas)
        accountBinanceService.syncAllInternalBalancesFromExternal();

        // 2) inicializa snapshots de tasas para hoy
        cryptoAverageRateService.inicializarCriptosDelDia(LocalDateTime.now());

        return ResponseEntity.ok().build();
    }

}
