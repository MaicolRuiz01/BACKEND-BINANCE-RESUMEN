package com.binance.web.transacciones;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.BinanceAPI.PaymentController;
import com.binance.web.BinanceAPI.SpotOrdersController;
import com.binance.web.BinanceAPI.TronScanController;
import com.binance.web.BinanceAPI.TronScanService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.Transacciones;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.service.AccountBinanceService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class TransaccionesServiceImpl implements TransaccionesService {

	private final TransaccionesRepository transaccionesRepository;
	private final AccountBinanceRepository accountBinanceRepository;
	private final TronScanService tronScanService;
	private final TronScanController tronController;
	private final SpotOrdersController spotController;
	private final PaymentController payController;

	private final TransaccionesRepository transRepo;
	private final AccountBinanceService accountBinanceService;

	@Override
    public Transacciones guardarTransaccion(TransaccionesDTO dto) throws IllegalArgumentException {
        // 1) Resolver cuentas (usuario, nombre o address)
        AccountBinance cuentaFrom = resolveAccount(dto.getCuentaFrom(), dto.getTipo());
        AccountBinance cuentaTo   = resolveAccount(dto.getCuentaTo(),   dto.getTipo());

        if (cuentaFrom == null || cuentaTo == null) {
            throw new IllegalArgumentException("CuentaFrom o CuentaTo no encontrada");
        }

        // 2) Resolver símbolo de la cripto transferida
        String symbol = resolveSymbol(dto); // USDT por omisión si no se puede inferir

        double monto = Optional.ofNullable(dto.getMonto()).orElse(0.0);
        if (monto <= 0) {
            throw new IllegalArgumentException("Monto inválido");
        }

        // 3) Persistir la transacción
        Transacciones t = new Transacciones();
        t.setCantidad(monto);
        t.setIdtransaccion(dto.getIdtransaccion());
        t.setTxId(dto.getTxId());
        t.setFecha(dto.getFecha());
        // Puedes guardar el canal en 'tipo' o, idealmente, agregar un campo cryptoSymbol en la entidad.
        t.setTipo(dto.getTipo());
        t.setCuentaFrom(cuentaFrom);
        t.setCuentaTo(cuentaTo);
        transaccionesRepository.save(t);

        // 4) Ajustar balances por cripto (resta en from, suma en to)
        accountBinanceService.updateOrCreateCryptoBalance(cuentaFrom.getId(), symbol, -Math.abs(monto));
        accountBinanceService.updateOrCreateCryptoBalance(cuentaTo.getId(),   symbol,  Math.abs(monto));

        return t;
    }

    // ---------- HELPERS ----------

    // Intenta por userBinance (si canal = BINANCEPAY), luego por name y por address
    private AccountBinance resolveAccount(String raw, String canalOTipo) {
        if (raw == null || raw.isBlank()) return null;

        // Canal específico: BINANCEPAY usa userBinance
        if ("BINANCEPAY".equalsIgnoreCase(canalOTipo)) {
            AccountBinance byUser = accountBinanceRepository.findByUserBinance(raw);
            if (byUser != null) return byUser;
        }

        // Nombre de cuenta
        AccountBinance byName = accountBinanceRepository.findByName(raw);
        if (byName != null) return byName;

        // Address
        return accountBinanceRepository.findAll().stream()
                .filter(a -> a.getAddress() != null && a.getAddress().equalsIgnoreCase(raw))
                .findFirst()
                .orElse(null);
    }

    // Si el DTO trae cryptoSymbol, úsalo. Si no, y 'tipo' parece un canal, cae a USDT.
    // Si 'tipo' NO es un canal, asume que es el símbolo (p.ej. "USDT", "USDC", "TRX").
    private String resolveSymbol(TransaccionesDTO dto) {
        // Si amplías tu DTO, prioriza dto.getCryptoSymbol()
        String tipo = Optional.ofNullable(dto.getTipo()).orElse("").trim();
        Set<String> CANALES = new HashSet<>(Arrays.asList("BINANCEPAY", "TRUST", "SPOT"));
        if (tipo.isEmpty()) return "USDT";
        return CANALES.contains(tipo.toUpperCase()) ? "USDT" : tipo;
    }

	@Override
	public List<TransaccionesDTO> saveAndFetchTodayTraspasos() {
		LocalDate today = LocalDate.now();
		LocalDateTime inicio = today.atStartOfDay();
		LocalDateTime fin = today.atTime(LocalTime.MAX);

		Set<String> existingIds = transRepo.findAll().stream().map(Transacciones::getIdtransaccion)
				.collect(Collectors.toSet());

		List<TransaccionesDTO> all = new ArrayList<>();

		ResponseEntity<List<TransaccionesDTO>> respTron = tronController.getTrustOutgoingTransfers();
		ResponseEntity<List<TransaccionesDTO>> respSpot = null; // Declara antes del try
		try {
			respSpot = spotController.getTraspasosNoRegistrados(100);
			if (respSpot.hasBody())
				all.addAll(respSpot.getBody());
		} catch (Exception e) {
			e.printStackTrace();
		}

		ResponseEntity<List<TransaccionesDTO>> respPay = payController.getTransaccionesNoRegistradas();

		if (respTron.hasBody())
			all.addAll(respTron.getBody());
		if (respSpot != null && respSpot.hasBody())
			all.addAll(respSpot.getBody());
		if (respPay.hasBody())
			all.addAll(respPay.getBody());

		for (TransaccionesDTO dto : all) {
			if (dto.getFecha().toLocalDate().equals(today) && !existingIds.contains(dto.getIdtransaccion())) {
				try {
					guardarTransaccion(dto);
					existingIds.add(dto.getIdtransaccion());
				} catch (Exception ignored) {
				}
			}
		}

		return transRepo.findByFechaBetween(inicio, fin).stream().map(TransaccionesDTO::fromEntity)
				.collect(Collectors.toList());
	}

}
