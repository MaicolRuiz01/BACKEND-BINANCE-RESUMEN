package com.binance.web.cryptoAverageRate;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.CryptoAverageRate;

import lombok.Data;
import lombok.RequiredArgsConstructor;

//package com.binance.web.controller;  <- ajusta a tu paquete real

@RestController
@RequestMapping("/api/crypto-average-rate")
@RequiredArgsConstructor
public class CryptoAverageRateController {

	private final CryptoAverageRateService cryptoAverageRateService;

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
		if (req.getCripto() == null || req.getTasaInicialUsdt() == null) {
			return ResponseEntity.badRequest().build();
		}

		CryptoAverageRate rate = cryptoAverageRateService.inicializarCripto(req.getCripto(), req.getTasaInicialUsdt(),
				LocalDateTime.now());

		return ResponseEntity.status(HttpStatus.CREATED).body(rate);
	}
}
