package com.binance.web.BinanceAPI;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.Entity.SpotOrder;
import com.binance.web.Repository.SpotOrderRepository;
import com.binance.web.model.OrdenSpotDTO;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ordenes-spot")
@RequiredArgsConstructor
public class SpotOrdersImportController {

	private final SpotOrderIngestService ingest;
	private final SpotOrderRepository spotOrderRepo;

	/** Importa para UNA cuenta con símbolos explícitos (csv). */
	@PostMapping("/importar")
	public ResponseEntity<String> importar(@RequestParam String cuenta, @RequestParam String simbolosCsv, // TRXUSDT,BTCUSDT
			@RequestParam(defaultValue = "50") int limite) {

		var symbols = Arrays.stream(simbolosCsv.split(",")).map(s -> s.trim().toUpperCase()).filter(s -> !s.isBlank())
				.toList();
		int n = ingest.importarCuenta(cuenta, symbols, limite);
		return ResponseEntity.ok("Órdenes importadas: " + n);
	}

	/**
	 * Importa para TODAS las cuentas BINANCE, símbolos deducidos automáticamente.
	 */
	@PostMapping("/importar/todas")
	public ResponseEntity<String> importarTodas(@RequestParam(defaultValue = "5") int limite) {
		int total = ingest.importarTodasLasCuentasAuto(limite);
		return ResponseEntity.ok("Órdenes importadas (total): " + total);
	}

	/** Listado para el front ya en DTO español (filtrable por cuenta, opcional). */
	@GetMapping("/listar")
	public ResponseEntity<List<OrdenSpotDTO>> listar(@RequestParam(required = false) String cuenta) {

	    List<SpotOrder> lista;

	    if (cuenta == null || cuenta.isBlank()) {
	        lista = spotOrderRepo.findAll();
	    } else {
	        lista = spotOrderRepo.findByCuentaBinance_NameOrderByFechaOperacionDesc(cuenta);
	    }

	    List<OrdenSpotDTO> out = lista.stream()
	            .sorted(Comparator.comparing(SpotOrder::getFechaOperacion).reversed())
	            .map(OrdenSpotDTO::fromEntity)
	            .toList();

	    return ResponseEntity.ok(out);
	}

}
