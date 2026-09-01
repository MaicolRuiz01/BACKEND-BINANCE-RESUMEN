package com.binance.web.serviceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.BankType;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SaleP2pAccountCop;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.SaleP2PRepository;
import com.binance.web.Repository.SaleP2pAccountCopRepository;
import com.binance.web.activacion.CuentaP2PSyncService;
import com.binance.web.service.AccountCopService;
import com.binance.web.service.RetiradorService;
import com.binance.web.util.CupoDiarioRules;

@Service
public class AccountCopServiceImpl implements AccountCopService {

	private final AccountCopRepository AccountCopRepository;
	private final SaleP2pAccountCopRepository saleP2pAccountCopRepository;
	private final SaleP2PRepository saleP2PRepository;
	private final RetiradorService retiradorService;
	private final CuentaP2PSyncService cuentaP2PSyncService;
	private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

	public AccountCopServiceImpl(AccountCopRepository AccountCopRepository, SaleP2PRepository saleP2PRepository, SaleP2pAccountCopRepository saleP2pAccountCopRepository, RetiradorService retiradorService, CuentaP2PSyncService cuentaP2PSyncService) {
	    this.AccountCopRepository = AccountCopRepository;
	    this.saleP2PRepository = saleP2PRepository;
	    this.saleP2pAccountCopRepository = saleP2pAccountCopRepository;
	    this.retiradorService = retiradorService;
	    this.cuentaP2PSyncService = cuentaP2PSyncService;
	}

	@Override
	@Transactional
	public List<AccountCop> findAllAccountCop() {
		List<AccountCop> cuentasCop = AccountCopRepository.findAllWithBrebeKeys();
		asegurarCupoHoyDeTodas(cuentasCop);
		return cuentasCop;
	}

	@Override
	public AccountCop findByIdAccountCop(Integer id) {
		return AccountCopRepository.findById(id).orElse(null);
	}

	@Override
	@Transactional
	public List<AccountCopRepository.SaldoView> findAllSaldos() {
		// Antes de devolver el cupo "liviano" (usado por el refresco en tiempo
		// real / SSE de las vistas de retiro), hay que asegurar que esté al día:
		// el reset diario normalmente ocurre al entrar a la vista de Cuentas
		// (findAllAccountCop), pero si nadie pasó por ahí todavía hoy, este
		// endpoint liviano podría devolver el cupo de ayer.
		List<AccountCop> cuentas = AccountCopRepository.findAll();
		asegurarCupoHoyDeTodas(cuentas);
		return AccountCopRepository.findAllSaldos();
	}

	/** Resetea a los máximos del día el cupo de cualquier cuenta cuyo cupo esté vencido o sin inicializar. */
	private void asegurarCupoHoyDeTodas(List<AccountCop> cuentas) {
		LocalDate hoy = LocalDate.now(ZONE_BOGOTA);
		for (AccountCop acc : cuentas) {
			if (acc.getBankType() == null) continue;
			boolean diaDistinto = acc.getCupoFecha() == null || !hoy.equals(acc.getCupoFecha());
			if (diaDistinto || acc.getCupoCajeroDisponibleHoy() == null || acc.getCupoCorresponsalDisponibleHoy() == null) {
				CupoDiarioRules.asegurarCupoHoy(acc);
				AccountCopRepository.save(acc);
			}
		}
	}


	@Override
    public void saveAccountCop(AccountCop accountCop) {
        if (accountCop.getName() == null || accountCop.getBalance() == null) {
            throw new IllegalArgumentException("El nombre de la cuenta y el saldo no pueden ser nulos.");
        }
        if (accountCop.getBankType() == null) {
            throw new IllegalArgumentException("bankType es obligatorio.");
        }

        // ✅ validar número de cuenta duplicado
        String num = accountCop.getNumeroCuenta();
        if (num != null && !num.isBlank()) {
            if (AccountCopRepository.existsByNumeroCuentaAndBankType(num.trim(), accountCop.getBankType())) {
                throw new IllegalArgumentException(
                    "Ya existe una cuenta " + accountCop.getBankType() + " con el número: " + num.trim());
            }
            accountCop.setNumeroCuenta(num.trim());
        }

        // ✅ saldo inicial del día
        accountCop.setSaldoInicialDelDia(accountCop.getBalance());

        // ✅ inicializar cupos al crear
        double cajero       = CupoDiarioRules.maxCajeroPorBanco(accountCop.getBankType());
        double corresponsal = CupoDiarioRules.maxCorresponsalPorBanco(accountCop.getBankType());
        accountCop.setCupoCajeroDisponibleHoy(cajero);
        accountCop.setCupoCorresponsalDisponibleHoy(corresponsal);
        accountCop.setCupoDiarioMax(cajero + corresponsal);
        accountCop.setCupoDisponibleHoy(cajero + corresponsal);
        accountCop.setCupoFecha(LocalDate.now(ZONE_BOGOTA));

        AccountCopRepository.save(accountCop);
    }

	@Override
	public void updateAccountCop(Integer id, AccountCop accountCop) {
	    AccountCop existing = AccountCopRepository.findById(id).orElse(null);
	    if (existing == null) {
	        throw new IllegalArgumentException("La cuenta con el ID " + id + " no existe.");
	    }

	    // ✅ validar número de cuenta duplicado al editar
	    String num = accountCop.getNumeroCuenta();
	    if (num != null && !num.isBlank()) {
	        BankType banco = accountCop.getBankType() != null ? accountCop.getBankType() : existing.getBankType();
	        if (AccountCopRepository.existsByNumeroCuentaAndBankTypeAndIdNot(num.trim(), banco, id)) {
	            throw new IllegalArgumentException(
	                "Ya existe otra cuenta " + banco + " con el número: " + num.trim());
	        }
	        existing.setNumeroCuenta(num.trim());
	    } else {
	        existing.setNumeroCuenta(null);
	    }

	    existing.setName(accountCop.getName());
	    existing.setBalance(accountCop.getBalance());
	    existing.setCedula(accountCop.getCedula());

	    // 👇 actualizar bankType si viene
	    if (accountCop.getBankType() != null) {
	        existing.setBankType(accountCop.getBankType());
	    }

	    AccountCopRepository.save(existing);
	}


	@Override
	public void deleteAccountCop(Integer id) {
		AccountCopRepository.deleteById(id);
	}
	
	@Override
	public List<SaleP2P> getSalesByAccountCopId(Integer accountCopId) {
	    AccountCop accountCop = AccountCopRepository.findById(accountCopId).orElse(null);
	    if (accountCop == null) {
	        return Collections.emptyList(); // O lanza una excepción personalizada
	    }

	    List<SaleP2P> sales = new ArrayList<>();
	    for (SaleP2pAccountCop detail : accountCop.getSaleP2pDetails()) {
	        SaleP2P sale = detail.getSaleP2p();
	        if (sale != null) {
	            sales.add(sale);
	        }
	    }

	    return sales;
	}

	@Override
	public void saveAccountCopSafe(AccountCop accountCop) {
	    AccountCop existing = AccountCopRepository.findById(accountCop.getId()).orElse(null);
	    if (existing == null) {
	        throw new RuntimeException("No existe AccountCop id " + accountCop.getId());
	    }

	    // ✅ SOLO actualiza campos que cambian en movimientos
	    existing.setBalance(accountCop.getBalance());
	    existing.setCupoDisponibleHoy(accountCop.getCupoDisponibleHoy());

	    // Asegura que el cupo diario (cajero/corresponsal) esté al día ANTES de
	    // guardar, para que el chequeo de retiro automático de abajo compare
	    // contra el cupo de HOY y no el de un día anterior.
	    CupoDiarioRules.asegurarCupoHoy(existing);
	    AccountCopRepository.save(existing);

	    // Retiro automático por P2P: si esta cuenta está seleccionada en P2P
	    // (activaParaP2P) y su saldo ya alcanzó el cupo disponible de hoy para
	    // cajero y/o corresponsal, dispara una Solicitud General automática al
	    // grupo de Retiradores — sin necesidad de que nadie la pida a mano.
	    retiradorService.verificarYDispararRetiroAutomaticoP2P(existing);
	}
	@Override
	@Transactional
	public String reconcileAccountCop(Integer accId) {
	    ZoneId zone = ZoneId.of("America/Bogota");
	    LocalDate today = LocalDate.now(zone);
	    LocalDateTime start = today.atStartOfDay();
	    LocalDateTime end = start.plusDays(1);

	    AccountCop acc = AccountCopRepository.findById(accId)
	        .orElseThrow(() -> new RuntimeException("No existe cuenta " + accId));

	    // BASE: si no tienes base, usa 0 (tu caso)
	    double baseBalance = 0.0;

	    double totalAll = saleP2pAccountCopRepository.sumAllByAccount(accId);
	    double totalToday = saleP2pAccountCopRepository.sumByAccountBetween(accId, start, end);

	    if (acc.getCupoDiarioMax() == null && acc.getBankType() != null) {
	        double c = CupoDiarioRules.maxCajeroPorBanco(acc.getBankType());
	        double r = CupoDiarioRules.maxCorresponsalPorBanco(acc.getBankType());
	        acc.setCupoDiarioMax(c + r);
	    }
	    double cupoMax = acc.getCupoDiarioMax() != null ? acc.getCupoDiarioMax() : 0.0;

	    double newBalance = baseBalance + totalAll;
	    double newCupoHoy = cupoMax - totalToday;

	    // por seguridad
	    if (newCupoHoy < 0) newCupoHoy = 0;

	    acc.setBalance(newBalance);
	    acc.setCupoDisponibleHoy(newCupoHoy);
	    acc.setCupoFecha(today);

	    AccountCopRepository.save(acc);

	    return "OK balance=" + newBalance + " cupoDisponibleHoy=" + newCupoHoy;
	}

	/**
	 * Sub-límite de seguridad: una cuenta a la que le quede menos de esto de cupo total no se
	 * elige aunque sea la más cercana al límite — está tan al borde que la próxima venta que le
	 * caiga la haría pasarse. Valores en MILES de COP, misma unidad que el resto de cupos.
	 */
	private static final double SUBLIMITE_CUPO_RESTANTE = 1_000.0;
	private static final int CUENTAS_A_ACTIVAR_POR_JORNADA = 5;

	@Override
	@Transactional
	public List<AccountCop> activarCincoCuentasMasCercanasAlCupo() {
	    List<AccountCop> todas = AccountCopRepository.findAll();

	    // Candidatas: no bloqueadas, con banco definido, y con cupo restante (cupoDiarioMax -
	    // balance) dentro del rango [SUBLIMITE_CUPO_RESTANTE, +inf). Un restante negativo o cero
	    // significa que ya se pasó del cupo; uno positivo pero por debajo del sub-límite significa
	    // que está demasiado cerca para arriesgarse con otra venta.
	    List<AccountCop> candidatas = new ArrayList<>();
	    for (AccountCop acc : todas) {
	        if (Boolean.TRUE.equals(acc.getBloqueada())) continue;
	        if (acc.getBankType() == null) continue;

	        CupoDiarioRules.asegurarCupoHoy(acc);

	        double cupoTotal = acc.getCupoDiarioMax() != null ? acc.getCupoDiarioMax() : 0.0;
	        double saldo = acc.getBalance() != null ? acc.getBalance() : 0.0;
	        double restante = cupoTotal - saldo;

	        if (restante >= SUBLIMITE_CUPO_RESTANTE) {
	            candidatas.add(acc);
	        }
	    }

	    // La más cerca de llenar su cupo total primero (menor cupo restante).
	    candidatas.sort(Comparator.comparingDouble(acc ->
	            (acc.getCupoDiarioMax() != null ? acc.getCupoDiarioMax() : 0.0)
	                    - (acc.getBalance() != null ? acc.getBalance() : 0.0)));

	    List<AccountCop> elegidas = candidatas.stream()
	            .limit(CUENTAS_A_ACTIVAR_POR_JORNADA)
	            .collect(Collectors.toList());

	    Set<Integer> idsElegidas = elegidas.stream().map(AccountCop::getId).collect(Collectors.toSet());

	    // Reemplaza el set de cuentas activas para P2P: solo las elegidas quedan activas — se
	    // apaga cualquier otra que estuviera activa de una jornada anterior, para no acumular
	    // cuentas activas sin control a medida que arrancan jornadas durante el día.
	    //
	    // Guardamos el estado ANTES de tocarlo para poder avisarle a Movimientos (vía
	    // CuentaP2PSyncService) exactamente qué cuentas pasaron de inactiva->activa (hay que
	    // arrancar el bot) o de activa->inactiva (hay que pararlo) — este método es el único
	    // lugar donde la selección automática cambia activaParaP2P en lote, sin pasar por
	    // AccountCopController.toggleActivaParaP2P.
	    for (AccountCop acc : todas) {
	        boolean estabaActivaAntes = Boolean.TRUE.equals(acc.getActivaParaP2P());
	        acc.setActivaParaP2P(idsElegidas.contains(acc.getId()));
	        cuentaP2PSyncService.sincronizar(acc, estabaActivaAntes);
	    }
	    AccountCopRepository.saveAll(todas);

	    return elegidas;
	}

}
