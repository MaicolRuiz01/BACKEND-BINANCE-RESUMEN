package com.binance.web.serviceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.BinanceAPI.BybitService;
import com.binance.web.BinanceAPI.SolscanService;
import com.binance.web.BinanceAPI.TronScanService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCryptoBalance;
import com.binance.web.Entity.AverageRate;

import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.AccountCryptoBalanceRepository;
import com.binance.web.Repository.AverageRateRepository;
import com.binance.web.Repository.CryptoAverageRateRepository;
import com.binance.web.model.CryptoBalanceDto;
import com.binance.web.service.AccountBinanceService;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountBinanceServiceImpl implements AccountBinanceService {

	private final BinanceService binanceService;
	private final AccountBinanceRepository accountBinanceRepository;
	private final TronScanService tronScanService;
	@Autowired
	private AverageRateRepository averageRateRepository;
	@Autowired
	private AccountCryptoBalanceRepository accountCryptoBalanceRepository;
	private final SolscanService solscanService;
	private final BybitService bybitService;
	private volatile Set<String> dynamicStables = new HashSet<>(Set.of("USDT"));
	private volatile long dynamicStablesTs = 0L;
	private static final long STABLES_TTL_MS = 5 * 60 * 1000; // 5 min
	private static final double STABLE_TOL = 0.02; // ±2 %
	@Autowired
	private CryptoAverageRateRepository cryptoAverageRateRepository;
	private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

	public AccountBinanceServiceImpl(AccountBinanceRepository accountBinanceRepository, BinanceService binanceService,
			TronScanService tronScanService, SolscanService solscanService, BybitService bybitService) {
		this.accountBinanceRepository = accountBinanceRepository;
		this.binanceService = binanceService;
		this.tronScanService = tronScanService;
		this.solscanService = solscanService;
		this.bybitService = bybitService;
	}

	private AccountCryptoBalance findOrCreate(AccountBinance account, String cryptoSymbol) {
		return accountCryptoBalanceRepository.findByAccountBinance_IdAndCryptoSymbol(account.getId(), cryptoSymbol)
				.orElseGet(() -> {
					AccountCryptoBalance b = new AccountCryptoBalance();
					b.setAccountBinance(account);
					b.setCryptoSymbol(cryptoSymbol);
					b.setBalance(0.0);
					return accountCryptoBalanceRepository.save(b);
				});
	}

	private Set<String> getDynamicStables() {
		long now = System.currentTimeMillis();
		if (now - dynamicStablesTs < STABLES_TTL_MS && !dynamicStables.isEmpty()) {
			return dynamicStables;
		}
		try {
			List<String> syms = accountCryptoBalanceRepository.findDistinctSymbols();
			Map<String, Double> pc = new HashMap<>();
			Set<String> st = new HashSet<>();
			st.add("USDT"); // siempre

			for (String s : syms) {
				if (s == null)
					continue;
				String u = s.trim().toUpperCase();
				if (u.isBlank() || "USDT".equals(u))
					continue;

				double px = usdtPriceRaw(u, pc); // precio directo
				if (px > 0 && Math.abs(px - 1.0) <= STABLE_TOL)
					st.add(u);
			}
			dynamicStables = st;
			dynamicStablesTs = now;
		} catch (Exception ignore) {
		}
		return dynamicStables;
	}

	// Precio con caché (respeta stables)
	private double usdtPrice(String symbol, Map<String, Double> cache) {
		if (symbol == null)
			return 0.0;
		String s = symbol.trim().toUpperCase();
		if (s.isEmpty())
			return 0.0;
		if ("USDT".equals(s) || getDynamicStables().contains(s))
			return 1.0;

		return cache.computeIfAbsent(s, key -> {
			try {
				Double px = binanceService.getPriceInUSDT(key);
				return px != null ? px : 0.0;
			} catch (Exception e) {
				return 0.0;
			}
		});
	}

	// Versión "cruda" (sin tratar como stable) – solo para detectar stables
	private double usdtPriceRaw(String symbol, Map<String, Double> cache) {
		if (symbol == null)
			return 0.0;
		String s = symbol.trim().toUpperCase();
		if (s.isEmpty())
			return 0.0;

		return cache.computeIfAbsent(s, key -> {
			try {
				Double px = binanceService.getPriceInUSDT(key);
				return px != null ? px : 0.0;
			} catch (Exception e) {
				return 0.0;
			}
		});
	}

	@Override
	@Transactional
	public void updateOrCreateCryptoBalance(Integer accountId, String cryptoSymbol, Double delta) {
	    if (accountId == null || cryptoSymbol == null || delta == null) return;

	    final String sym = cryptoSymbol.trim().toUpperCase(); // 👈 normaliza

	    AccountBinance accountRef = accountBinanceRepository.getReferenceById(accountId);

	    AccountCryptoBalance bal = accountCryptoBalanceRepository
	        .findByAccountBinanceIdAndCryptoSymbol(accountId, sym)  // usa el normalizado
	        .orElseGet(() -> {
	            AccountCryptoBalance b = new AccountCryptoBalance();
	            b.setAccountBinance(accountRef);
	            b.setCryptoSymbol(sym); // 👈 guardas normalizado
	            b.setBalance(0.0);
	            return b;
	        });

	    double current = bal.getBalance() != null ? bal.getBalance() : 0.0;
	    bal.setBalance(current + delta);
	    accountCryptoBalanceRepository.save(bal);
	}


	@Override
	public List<AccountBinance> findAllAccountBinance() {
		return accountBinanceRepository.findAll();
	}

	@Override
	public AccountBinance findByIdAccountBinance(Integer id) {
		return accountBinanceRepository.findById(id).orElse(null);
	}

	@Override
	public void saveAccountBinance(AccountBinance accountBinance) {
		String addr = accountBinance.getAddress();
		if (addr != null && !addr.isBlank()) {
			if (accountBinanceRepository.existsByAddress(addr.trim())) {
				throw new IllegalArgumentException(
					"Ya existe una cuenta con la wallet: " + addr.trim());
			}
			accountBinance.setAddress(addr.trim());
		}
		accountBinanceRepository.save(accountBinance);
	}

	@Override
	public void updateAccountBinance(Integer id, AccountBinance updatedAccountBinance) {
		AccountBinance existing = accountBinanceRepository.findById(id).orElse(null);
		if (existing != null) {
			String addr = updatedAccountBinance.getAddress();
			if (addr != null && !addr.isBlank()) {
				if (accountBinanceRepository.existsByAddressAndIdNot(addr.trim(), id)) {
					throw new IllegalArgumentException(
						"Ya existe otra cuenta con la wallet: " + addr.trim());
				}
				existing.setAddress(addr.trim());
			} else {
				existing.setAddress(null);
			}
			existing.setName(updatedAccountBinance.getName());
			existing.setReferenceAccount(updatedAccountBinance.getReferenceAccount());
			existing.setCorreo(updatedAccountBinance.getCorreo());
			existing.setUserBinance(updatedAccountBinance.getUserBinance());
			existing.setTipo(updatedAccountBinance.getTipo());
			existing.setActiva(updatedAccountBinance.getActiva());
			accountBinanceRepository.save(existing);
		}
	}

	@Override
	public void deleteAccountBinance(Integer id) {
		accountBinanceRepository.deleteById(id);
	}

	@Override
	public AccountBinance findByName(String name) {
		return accountBinanceRepository.findByName(name);
	}

	/**
	 * @deprecated Antes restaba el "balance" en USD de AccountBinance. Ahora
	 *             redirige a USDT por compatibilidad. Cambia las llamadas a
	 *             updateOrCreateCryptoBalance/subtractCryptoBalance.
	 */
	@Override
	@Deprecated
	public void subtractBalance(String name, Double amount) {
		if (amount == null || amount <= 0)
			return;

		AccountBinance account = accountBinanceRepository.findByName(name);
		if (account == null) {
			throw new RuntimeException("Account not found: " + name);
		}

		// Por compatibilidad: asumimos que el viejo "balance" era USDT
		updateOrCreateCryptoBalance(account.getId(), "USDT", -Math.abs(amount));
		System.out.println("🔴 Restando " + amount + " USDT a cuenta: " + name);
	}

	@Override
	public Double getTotalExternalBalance() {
		List<AccountBinance> cuentas = accountBinanceRepository.findAll();
		double total = 0.0;

		for (AccountBinance account : cuentas) {
			try {
				String tipo = account.getTipo() != null ? account.getTipo().toUpperCase() : "";
				if ("BINANCE".equals(tipo)) {
					Double balance = binanceService.getGeneralBalanceInUSDT(account.getName());
					total += balance != null ? balance : 0.0;
				} else if ("TRUST".equals(tipo) || "TRUSTWALLET".equals(tipo)) {
					total += tronScanService.getTotalAssetTokenOverview(account.getAddress());
				} else if ("SOLANA".equals(tipo) || "PHANTOM".equals(tipo)) {
					total += solscanService.getTotalAssetUsd(account.getAddress());
				} else if ("BYBIT".equals(tipo) || "BYBIP".equals(tipo)) {
					Map<String, Double> balances = bybitService.getBalancesByAsset(account.getApiKey(), account.getApiSecret());
					Map<String, Double> priceCache = new HashMap<>();
					for (Map.Entry<String, Double> e : balances.entrySet()) {
						double qty = e.getValue() != null ? e.getValue() : 0.0;
						if (qty <= 0) continue;
						total += qty * usdtPrice(e.getKey(), priceCache);
					}
				}
			} catch (Exception e) {
				System.out.println("⚠️ Error con cuenta " + account.getName() + ": " + e.getMessage());
			}
		}
		return total;
	}

	@Override
	public Double getEstimatedUSDTBalance(String name) {
		// Aquí delegamos al método que calcula sumando cada activo * precio USDT
		return binanceService.getEstimatedSpotBalance(name);
	}

	@Override
	public Double getFundingUSDTBalance(String name) {
		// delegamos al BinanceService
		return binanceService.getFundingAssetBalance(name, "USDT");
	}

	// ✅ Nuevo método para sustraer un balance de cripto
	@Override
	public void subtractCryptoBalance(Integer accountId, String cryptoSymbol, Double amount) {
		if (amount == null || amount <= 0)
			return;
		updateOrCreateCryptoBalance(accountId, cryptoSymbol, -Math.abs(amount));
	}

	// ✅ Obtiene balance de una cripto por nombre de cuenta
	@Override
	public Double getCryptoBalance(String accountName, String cryptoSymbol) {
		AccountBinance account = accountBinanceRepository.findByName(accountName);
		if (account == null)
			return null;

		return account.getCryptoBalances().stream().filter(b -> cryptoSymbol.equalsIgnoreCase(b.getCryptoSymbol()))
				.map(AccountCryptoBalance::getBalance).findFirst().orElse(0.0);
	}

	@Override
	public Double getUSDTBalance(String name) {
		AccountBinance account = accountBinanceRepository.findByName(name);
		if (account == null)
			return null;

		String tipo = account.getTipo() != null ? account.getTipo().toUpperCase() : "";
		try {
			if ("BINANCE".equals(tipo)) {
				return binanceService.getGeneralBalanceInUSDT(name);
			} else if ("TRUST".equals(tipo) || "TRUSTWALLET".equals(tipo)) {
				return tronScanService.getTotalAssetTokenOverview(account.getAddress());
			} else if ("SOLANA".equals(tipo) || "PHANTOM".equals(tipo)) {
				return solscanService.getTotalAssetUsd(account.getAddress()); // USD ≈ USDT
			} else if ("BYBIT".equals(tipo) || "BYBIP".equals(tipo)) {
				Map<String, Double> balances = bybitService.getBalancesByAsset(account.getApiKey(), account.getApiSecret());
				Map<String, Double> priceCache = new HashMap<>();
				double total = 0.0;
				for (Map.Entry<String, Double> e : balances.entrySet()) {
					double qty = e.getValue() != null ? e.getValue() : 0.0;
					if (qty <= 0) continue;
					total += qty * usdtPrice(e.getKey(), priceCache);
				}
				return total;
			} else {
				return 0.0;
			}
		} catch (Exception e) {
			return 0.0;
		}
	}

	@Override
	public BigDecimal getTotalBalance() {
		// primero calculamos el total en USDT (igual que arriba)
		List<AccountCryptoBalanceRepository.SymbolQty> rows = accountCryptoBalanceRepository.sumBySymbol();

		Map<String, Double> priceCache = new HashMap<>();
		double totalUsdt = 0.0;

		for (var r : rows) {
			String sym = r.getSymbol();
			double qty = r.getQty() != null ? r.getQty() : 0.0;
			if (qty <= 0)
				continue;

			double px = usdtPrice(sym, priceCache);
			totalUsdt += qty * px;
		}

		// aplica tasa — si no hay tasa registrada, devuelve el valor en USDT
		double tasa = averageRateRepository.findTopByOrderByIdDesc()
				.map(AverageRate::getAverageRate)
				.filter(r -> r != null && r > 0)
				.orElse(1.0);

		return BigDecimal.valueOf(totalUsdt).multiply(BigDecimal.valueOf(tasa));
	}

	@Override
	public BigDecimal getTotalBalanceInterno() {
	    // 1) Saldos internos agregados por símbolo
	    List<AccountCryptoBalanceRepository.SymbolQty> rows =
	            accountCryptoBalanceRepository.sumBySymbol();

	    // 2) Tasas promedio del día por cripto (TRX, SOL, SPK, etc.)
	    Map<String, Double> tasasHoy = getTodayCryptoAverageRates();

	    double totalUsdt = 0.0;

	    for (var r : rows) {
	        String sym = Optional.ofNullable(r.getSymbol())
	                .orElse("")
	                .trim()
	                .toUpperCase();
	        double qty = r.getQty() != null ? r.getQty() : 0.0;

	        if (qty <= 0 || sym.isEmpty()) continue;

	        double pxUsdt;

	        if ("USDT".equals(sym)) {
	            // USDT ya está en USDT
	            pxUsdt = 1.0;
	        } else {
	            // Primero intentamos con la tasa promedio del día
	            pxUsdt = Optional.ofNullable(tasasHoy.get(sym)).orElse(0.0);

	            // (Opcional) fallback: si no hay tasa promedio, usamos precio de mercado
	            if (pxUsdt <= 0.0) {
	                pxUsdt = usdtPrice(sym, new HashMap<>());
	            }
	        }

	        if (pxUsdt <= 0.0) continue;  // si sigue sin valor, lo ignoramos

	        totalUsdt += qty * pxUsdt;
	    }

	    // 👉 devuelve el saldo interno TOTAL en USDT
	    return BigDecimal.valueOf(totalUsdt);
	}



	private double priceInUSDT(String symbol) {
		if (symbol == null)
			return 0.0;
		String s = symbol.trim().toUpperCase();
		if ("USDT".equals(s) || "USDC".equals(s))
			return 1.0; // stables 1:1
		try {
			// Usa un método del BinanceService que devuelva el precio spot en USDT.
			// Si no lo tienes, crea uno público en BinanceService que llame a
			// /api/v3/ticker/price.
			Double px = binanceService.getPriceInUSDT(s);
			return px != null ? px : 0.0;
		} catch (Exception e) {
			return 0.0;
		}
	}

	// Balance interno de UNA cuenta, convertido a USD
	@Override
	public Double getInternalUsdBalance(String accountName) {
		List<AccountCryptoBalanceRepository.SymbolQty> rows = accountCryptoBalanceRepository
				.sumBySymbolForAccount(accountName);

		Map<String, Double> priceCache = new HashMap<>();
		double totalUsdt = 0.0;

		for (var r : rows) {
			String sym = r.getSymbol();
			double qty = r.getQty() != null ? r.getQty() : 0.0;
			if (qty <= 0)
				continue;

			double px = usdtPrice(sym, priceCache);
			totalUsdt += qty * px;
		}
		return totalUsdt;
	}

	// (Opcional) Suma de TODAS las cuentas, convertido a USD
	@Override
	public Double getTotalInternalUsdBalance() {
		return accountBinanceRepository.findAll().stream().flatMap(a -> a.getCryptoBalances().stream())
				.filter(Objects::nonNull).mapToDouble(b -> {
					double qty = b.getBalance() != null ? b.getBalance() : 0.0;
					double px = priceInUSDT(b.getCryptoSymbol());
					return qty * px;
				}).sum();
	}

	// AccountBinanceServiceImpl.java

	@Override
	@Transactional
	public void syncInternalBalancesFromExchange(String accountName) {
		AccountBinance account = accountBinanceRepository.findByName(accountName);
		if (account == null)
			throw new RuntimeException("Cuenta no encontrada: " + accountName);
		if (!"BINANCE".equalsIgnoreCase(account.getTipo())) {
			throw new RuntimeException("Solo soportado para cuentas BINANCE por ahora");
		}

		// 1) Traer snapshot externo (Spot + Funding)
		Map<String, Double> external = binanceService.getAllBalancesByAsset(accountName);

		// 2) Indexar existentes
		Map<String, AccountCryptoBalance> existing = account.getCryptoBalances().stream()
				.collect(Collectors.toMap(b -> b.getCryptoSymbol().toUpperCase(), b -> b));

		// 3) Actualizar / crear
		Set<String> seen = new HashSet<>();
		for (Map.Entry<String, Double> e : external.entrySet()) {
			String sym = e.getKey().toUpperCase();
			double qty = e.getValue();

			AccountCryptoBalance bal = existing.get(sym);
			if (bal == null) {
				bal = new AccountCryptoBalance();
				bal.setAccountBinance(account);
				bal.setCryptoSymbol(sym);
				bal.setBalance(qty);
				account.getCryptoBalances().add(bal);
			} else {
				bal.setBalance(qty);
			}
			seen.add(sym);
		}

		// 4) Eliminar las que ya no existen en el exchange (por snapshot real)
		account.getCryptoBalances().removeIf(b -> !seen.contains(b.getCryptoSymbol().toUpperCase()));

		accountBinanceRepository.save(account);
	}

	@Override
	@Transactional
	public void syncAllInternalBalancesFromExchange() {
		List<AccountBinance> accounts = accountBinanceRepository.findByTipoAndActivaTrue("BINANCE");
		for (AccountBinance a : accounts) {
			syncInternalBalancesFromExchange(a.getName());
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Double> getExternalBalancesSnapshot(String name) {
		AccountBinance acc = accountBinanceRepository.findByName(name);
		if (acc == null)
			throw new RuntimeException("Cuenta no encontrada: " + name);

		String tipo = acc.getTipo() != null ? acc.getTipo().toUpperCase() : "";
		switch (tipo) {
		case "BINANCE":
			return binanceService.getAllBalancesByAsset(name);
		case "TRUST":
		case "TRUSTWALLET":
			String addrTron = acc.getAddress();
			if (addrTron == null || addrTron.isBlank())
				throw new RuntimeException("Wallet address vacío para " + name);
			return tronScanService.getBalancesByAsset(addrTron);
		case "SOLANA":
		case "PHANTOM":
			String addrSol = acc.getAddress();
			if (addrSol == null || addrSol.isBlank())
				throw new RuntimeException("Wallet address vacío para " + name);
			return solscanService.getBalancesByAsset(addrSol);
		case "BYBIT":
		case "BYBIP": // typo común — se acepta igual
			// Bybit es un exchange: el saldo se lee por API (Unified + Funding), NO on-chain.
			return bybitService.getBalancesByAsset(acc.getApiKey(), acc.getApiSecret());
		default:
			return Collections.emptyMap();
		}
	}

	@Override
	@Transactional
	public Map<String, Double> syncInternalBalancesFromExternal(String name) {
		Map<String, Double> snapshot = getExternalBalancesSnapshot(name);
		AccountBinance acc = accountBinanceRepository.findByName(name);
		if (acc == null)
			throw new RuntimeException("Cuenta no encontrada: " + name);

		// Actualiza/crea cada cripto
		for (Map.Entry<String, Double> e : snapshot.entrySet()) {
			AccountCryptoBalance b = findOrCreate(acc, e.getKey());
			b.setBalance(e.getValue());
			accountCryptoBalanceRepository.save(b);
		}
		// (Opcional) elimina internas que ya no existan externamente
		List<AccountCryptoBalance> actuales = accountCryptoBalanceRepository.findByAccountBinance_Id(acc.getId());
		Set<String> vigentes = snapshot.keySet().stream().map(String::toUpperCase).collect(Collectors.toSet());
		for (AccountCryptoBalance b : actuales) {
			if (!vigentes.contains(b.getCryptoSymbol().toUpperCase())) {
				accountCryptoBalanceRepository.delete(b);
			}
		}
		return snapshot;
	}

	@Override
	@Transactional
	public void syncAllInternalBalancesFromExternal() {
		accountBinanceRepository.findAll()
			.stream()
			.filter(acc -> Boolean.TRUE.equals(acc.getActiva()))
			.forEach(acc -> {
				try {
					syncInternalBalancesFromExternal(acc.getName());
				} catch (Exception e) {
					System.out.println("⚠️ No se pudo sincronizar " + acc.getName() + ": " + e.getMessage());
				}
			});
	}
	
	@Override
	public Double getTotalCryptoBalanceInterno(String cripto) {
	    if (cripto == null) return 0.0;

	    String sym = cripto.trim().toUpperCase();
	    if (sym.isEmpty()) return 0.0;

	    // Usamos el agregado que ya tienes: sumBySymbol()
	    List<AccountCryptoBalanceRepository.SymbolQty> rows =
	            accountCryptoBalanceRepository.sumBySymbol();

	    return rows.stream()
	            .filter(r -> sym.equalsIgnoreCase(r.getSymbol()))
	            .map(r -> r.getQty() != null ? r.getQty() : 0.0)
	            .findFirst()
	            .orElse(0.0);
	}
	
	@Override
	public Double getTotalCryptoBalanceExterno(String cripto) {
	    String c = cripto.toUpperCase();
	    double total = 0.0;

	    for (AccountBinance acc : accountBinanceRepository.findByTipoAndActivaTrue("BINANCE")) {
	        try {
	            Map<String, Double> snap = getExternalBalancesSnapshot(acc.getName());
	            total += snap.getOrDefault(c, 0.0);
	        } catch (Exception e) {
	            System.err.println("⚠ No pude leer " + c + " para " + acc.getName() + ": " + e.getMessage());
	        }
	    }
	    return total;
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<CryptoBalanceDto> getInternalBalancesDetail(String accountName) {
	    AccountBinance acc = accountBinanceRepository.findByName(accountName);
	    if (acc == null) {
	        throw new RuntimeException("Cuenta no encontrada: " + accountName);
	    }

	    Map<String, Double> priceCache = new HashMap<>();
	    List<CryptoBalanceDto> out = new ArrayList<>();

	    for (AccountCryptoBalance b : acc.getCryptoBalances()) {
	        double qty = b.getBalance() != null ? b.getBalance() : 0.0;
	        if (qty <= 0) continue;

	        String sym = b.getCryptoSymbol();
	        double px = usdtPrice(sym, priceCache); // ya respeta stables
	        double usdtVal = qty * px;

	        out.add(new CryptoBalanceDto(sym, qty, usdtVal));
	    }

	    // opcional: ordenar por valor en USDT desc
	    out.sort(Comparator.comparing(CryptoBalanceDto::getUsdtValue).reversed());

	    return out;
	}

	/**
	 * Detalle de criptos por cuenta desde el saldo EXTERNO REAL (consulta en vivo al
	 * exchange / on-chain), NO el interno. Mismo formato que getInternalBalancesDetail.
	 */
	@Override
	public List<CryptoBalanceDto> getExternalBalancesDetail(String accountName) {
	    Map<String, Double> external = getExternalBalancesSnapshot(accountName);
	    Map<String, Double> priceCache = new HashMap<>();
	    List<CryptoBalanceDto> out = new ArrayList<>();

	    for (Map.Entry<String, Double> e : external.entrySet()) {
	        String sym = e.getKey() != null ? e.getKey().trim().toUpperCase() : "";
	        double qty = e.getValue() != null ? e.getValue() : 0.0;
	        if (sym.isEmpty() || qty <= 0) continue;

	        double px = usdtPrice(sym, priceCache);
	        double usdtVal = qty * px;
	        out.add(new CryptoBalanceDto(sym, qty, usdtVal));
	    }

	    out.sort(Comparator.comparing(CryptoBalanceDto::getUsdtValue).reversed());
	    return out;
	}

	private Map<String, Double> getTodayCryptoAverageRates() {
	    LocalDate hoy = LocalDate.now(ZONE_BOGOTA);

	    return cryptoAverageRateRepository.findByDia(hoy).stream()
	            .filter(r -> r.getCripto() != null)
	            .collect(Collectors.toMap(
	                    r -> r.getCripto().trim().toUpperCase(),
	                    r -> Optional.ofNullable(r.getTasaPromedioDia()).orElse(0.0),
	                    (a, b) -> b   // por si se repite, nos quedamos con el último
	            ));
	}


}
