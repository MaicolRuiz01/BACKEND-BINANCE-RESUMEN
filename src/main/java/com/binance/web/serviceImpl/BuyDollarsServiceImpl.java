package com.binance.web.serviceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.commons.codec.digest.DigestUtils;

import com.binance.web.BinanceAPI.PaymentController;
import com.binance.web.BinanceAPI.SolanaController;
import com.binance.web.BinanceAPI.SpotOrdersController;
import com.binance.web.BinanceAPI.TronScanController;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCryptoBalance;
import com.binance.web.Entity.AverageRate;
import com.binance.web.Entity.BuyDollars;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.model.BuyDollarsDto;
import com.binance.web.model.TransaccionesDTO;
import com.binance.web.Entity.Transacciones;
import com.binance.web.transacciones.TransaccionesRepository;
import com.binance.web.util.TraspasoWalletService;
import com.binance.web.util.TraspasoBybitService;
import com.binance.web.service.AccountBinanceService;
import com.binance.web.service.AverageRateService;
import com.binance.web.service.BuyDollarsService;

import org.springframework.dao.DataIntegrityViolationException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;


@Service
public class BuyDollarsServiceImpl implements BuyDollarsService {

    @Autowired
    private BuyDollarsRepository buyDollarsRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ClienteRepository clienteRepository;

	
	@Autowired
	private AverageRateService averageRateService;
	@Autowired
	private PaymentController binancePayController;
	@Autowired
	private SpotOrdersController spotOrdersController;
	@Autowired
	private TronScanController tronScanController;
	@Autowired
	private AccountBinanceService accountBinanceService; 
	@Autowired
	private AccountBinanceRepository accountBinanceRepository;
	@Autowired
	private TransaccionesRepository transaccionesRepository;
	@Autowired
	private TraspasoWalletService traspasoWalletService;
	@Autowired
	private TraspasoBybitService traspasoBybitService;
	@Autowired
	private SolanaController solanaController;
	@Autowired
	private com.binance.web.BinanceAPI.BybitService bybitService;

	@Override
	public BuyDollars getLastBuyDollars() {
		BuyDollars lastBuy = buyDollarsRepository.findTopByOrderByDateDesc();
		if (lastBuy == null) {
			throw new RuntimeException("No hay registros de BuyDollars");
		}
		return lastBuy;
	}
	
	@Override
	public List<BuyDollars> getAllBuyDollars() {
	    return buyDollarsRepository.findAll(Sort.by(Sort.Direction.DESC, "date"));
	}
	
	 @Override
	    @Transactional
	    public BuyDollars updateBuyDollars(Integer id, BuyDollarsDto dto) {
	        BuyDollars existing = buyDollarsRepository.findById(id)
	            .orElseThrow(() -> new RuntimeException("Compra con ID " + id + " no encontrada"));

	        // ===== Valores ANTIGUOS (el titular real puede ser proveedor O cliente) =====
	        Double   oldAmount       = existing.getAmount();
	        Double   oldPesos        = existing.getPesos() != null ? existing.getPesos() : 0.0;
	        Supplier oldSupplier     = existing.getSupplier();
	        Cliente  oldCliente      = existing.getCliente();
	        AccountBinance oldAccount = existing.getAccountBinance();
	        String   oldCryptoSymbol = existing.getCryptoSymbol();

	        // ===== Valores NUEVOS (con fallbacks para no romper si el front no manda algún campo) =====
	        Double  newAmount       = dto.getAmount()  != null ? dto.getAmount()  : oldAmount;
	        Double  newTasa         = dto.getTasa()    != null ? dto.getTasa()    : existing.getTasa();
	        Double  newPesos        = (newAmount != null && newTasa != null) ? newAmount * newTasa : 0.0;
	        Integer newSupplierId   = dto.getSupplierId();
	        Integer newClienteId    = dto.getClienteId();
	        Integer newAccountId    = dto.getAccountBinanceId();
	        String  newCryptoSymbol = dto.getCryptoSymbol() != null ? dto.getCryptoSymbol() : oldCryptoSymbol;

	        // Debe reasignarse a un proveedor O a un cliente (uno de los dos).
	        if (newSupplierId == null && newClienteId == null) {
	            throw new RuntimeException("Debe especificar un proveedor o un cliente para reasignar la compra");
	        }

	        // 1. REVERTIR el saldo del titular ANTERIOR — sea proveedor o cliente.
	        //    (En "debe/debemos" del cliente el signo es el mismo que en la asignación: al asignar
	        //     se sumó oldPesos, así que aquí se resta oldPesos para deshacerlo.)
	        if (oldSupplier != null) {
	            double bal = oldSupplier.getBalance() != null ? oldSupplier.getBalance() : 0.0;
	            oldSupplier.setBalance(bal - oldPesos);
	            supplierRepository.save(oldSupplier);
	        }
	        if (oldCliente != null) {
	            double sal = oldCliente.getSaldo() != null ? oldCliente.getSaldo() : 0.0;
	            oldCliente.setSaldo(sal - oldPesos);
	            clienteRepository.save(oldCliente);
	        }

	        // 2. Actualizar los datos de la compra.
	        existing.setAmount(newAmount);
	        existing.setTasa(newTasa);
	        existing.setPesos(newPesos);
	        existing.setCryptoSymbol(newCryptoSymbol);

	        // 3. Reasignar el titular NUEVO y APLICAR el saldo. Proveedor tiene prioridad si vinieran ambos.
	        //    Se limpia SIEMPRE el otro lado para que la compra nunca quede con proveedor y cliente a la vez.
	        if (newSupplierId != null) {
	            Supplier newSupplier = supplierRepository.findById(newSupplierId)
	                .orElseThrow(() -> new RuntimeException("Nuevo proveedor no encontrado"));
	            existing.setSupplier(newSupplier);
	            existing.setCliente(null);
	            double bal = newSupplier.getBalance() != null ? newSupplier.getBalance() : 0.0;
	            newSupplier.setBalance(bal + newPesos);
	            supplierRepository.save(newSupplier);
	        } else {
	            Cliente newCliente = clienteRepository.findById(newClienteId)
	                .orElseThrow(() -> new RuntimeException("Nuevo cliente no encontrado"));
	            existing.setCliente(newCliente);
	            existing.setSupplier(null);
	            double sal = newCliente.getSaldo() != null ? newCliente.getSaldo() : 0.0;
	            newCliente.setSaldo(sal + newPesos);
	            clienteRepository.save(newCliente);
	        }

	        // 4. Cuenta Binance / saldo cripto:
	        //    El cripto ya entró físicamente a una cuenta al importar la compra. Reasignar el PROVEEDOR
	        //    (o el cliente) NO debe tocar el saldo cripto. Solo si el operario cambia la CUENTA de verdad
	        //    se mueve el cripto de una cuenta a la otra. Así reasignar un proveedor no descuadra el cripto.
	        if (newAccountId != null && (oldAccount == null || !newAccountId.equals(oldAccount.getId()))) {
	            if (oldAccount != null && oldAmount != null) {
	                accountBinanceService.subtractCryptoBalance(oldAccount.getId(), oldCryptoSymbol, oldAmount * 1000.0);
	            }
	            AccountBinance newAccount = accountBinanceRepository.findById(newAccountId)
	                .orElseThrow(() -> new RuntimeException("Nueva cuenta de Binance no encontrada"));
	            existing.setAccountBinance(newAccount);
	            if (newAmount != null) {
	                accountBinanceService.updateOrCreateCryptoBalance(newAccount.getId(), newCryptoSymbol, newAmount * 1000.0);
	            }
	        }
	        // Si la cuenta no cambió, el saldo cripto se deja intacto a propósito.

	        return buyDollarsRepository.save(existing);
	    }

	@Override
    public void registrarComprasAutomaticamente() {
        registrarComprasAutomaticamente(0);
    }

    /** Igual que el automático, pero permite mirar depósitos de Bybit de hasta {diasAtras} días
     *  atrás (0 = solo hoy). Solo afecta la fuente Bybit; las demás siguen siendo del día.
     *  Uso manual para probar/reprocesar movimientos de días anteriores. */
    @Override
    public void registrarComprasAutomaticamente(int diasAtras) {
        try {
            // ✅ Modificación 1: Los controladores devuelven una lista de DTOs genéricos
            List<BuyDollarsDto> binancePay = binancePayController.getComprasNoRegistradas().getBody();
            List<BuyDollarsDto> spot = spotOrdersController.getComprasNoRegistradas(20).getBody();
            List<BuyDollarsDto> trust = tronScanController.getUSDTIncomingTransfers().getBody();
           // List<BuyDollarsDto> sol = solanaController.getSolanaIncomingTransfers().getBody(); miton dijo que no interesaba las entradas de solana
            Set<String> existentes = buyDollarsRepository.findAll().stream()
                .map(BuyDollars::getIdDeposit)
                .collect(Collectors.toSet());

            // Depósitos entrantes de las cuentas Bybit (antes no se consultaban en absoluto).
            // Excluye internamente los que vengan de una wallet propia registrada (traspaso).
            Set<String> ownAddresses = accountBinanceRepository.findAllAddresses();
            java.time.LocalDate desde = java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota"))
                    .minusDays(Math.max(0, diasAtras));
            List<BuyDollarsDto> bybit = new ArrayList<>();
            int cuentasBybit = 0;
            for (AccountBinance acc : accountBinanceRepository.findAll()) {
                String tipo = acc.getTipo() != null ? acc.getTipo().trim().toUpperCase() : "";
                if (!tipo.startsWith("BYBI")) continue; // BYBIT y el typo común BYBIP
                if (!Boolean.TRUE.equals(acc.getActiva())) continue;
                cuentasBybit++;
                bybit.addAll(bybitService.getIncomingDeposits(
                        acc.getApiKey(), acc.getApiSecret(), acc.getName(), existentes, ownAddresses, desde));
            }
            System.out.println("[Bybit][DIAG] registrarCompras: " + cuentasBybit + " cuenta(s) Bybit activa(s), "
                    + bybit.size() + " depósito(s) candidato(s).");

            List<BuyDollarsDto> todas = new ArrayList<>();
            if (binancePay != null) todas.addAll(binancePay);
            if (spot != null) todas.addAll(spot);
            if (trust != null) todas.addAll(trust);
            todas.addAll(bybit);
            //if (sol != null) todas.addAll(sol);
            todas.sort(Comparator.comparing(BuyDollarsDto::getDate));

            for (BuyDollarsDto dto : todas) {
                if (dto.getIdDeposit() == null || existentes.contains(dto.getIdDeposit())) {
                    continue;
                }

                AccountBinance account = accountBinanceRepository.findByName(dto.getNameAccount());
                if (account == null) {
                    continue;
                }
                String dedupeKey = buildDedupeKey(dto);
                if (buyDollarsRepository.findByDedupeKey(dedupeKey).isPresent()) {
                    continue;
                  }

                // 🔁 ¿Es un TRASPASO (no una compra)? Detección REAL por HASH on-chain:
                //    si el hash de este depósito coincide con un RETIRO de una de tus cuentas Bybit,
                //    es un traspaso interno y SABEMOS de qué cuenta salió (cuentaFrom real → "Javier").
                //    Se prueban ambos identificadores (txId e idDeposit) porque según la fuente uno
                //    u otro trae el hash on-chain.
                //    Los traspasos hacia wallets propias ya se excluyen antes (por dirección propia),
                //    así que aquí SOLO es traspaso si el hash matchea un retiro nuestro. Se quitó el
                //    fallback hardcodeado (esWalletTraspaso) que marcaba como traspaso depósitos
                //    externos reales (compras) solo porque la dirección estaba en una lista fija.
                AccountBinance origenBybit = traspasoBybitService.cuentaOrigenPorHash(dto.getTxId());
                if (origenBybit == null) origenBybit = traspasoBybitService.cuentaOrigenPorHash(dto.getIdDeposit());
                boolean esTraspaso = origenBybit != null;

                if (esTraspaso) {
                    // El cripto real sí entró (se ajusta el saldo), pero se registra como
                    // Transacción/traspaso y NO como BuyDollars (no afecta proveedores/clientes).
                    accountBinanceService.updateOrCreateCryptoBalance(account.getId(), dto.getCryptoSymbol(), dto.getAmount());
                    String txHash = dto.getIdDeposit();
                    if (txHash == null || !transaccionesRepository.existsByTxId(txHash)) {
                        Transacciones t = new Transacciones();
                        t.setIdtransaccion("TRASPASO-" + (origenBybit != null ? "BYBIT-" : "EXT-") + txHash);
                        t.setTxId(txHash);
                        t.setCantidad(dto.getAmount());     // crudo (USDT reales)
                        t.setFecha(dto.getDate());
                        t.setTipo(dto.getCryptoSymbol());
                        t.setCuentaTo(account);             // entró a nuestra cuenta
                        t.setCuentaFrom(origenBybit);       // ← cuenta Bybit REAL si se detectó por hash; null si solo por wallet
                        transaccionesRepository.save(t);
                    }
                    existentes.add(dto.getIdDeposit());
                    continue;
                }

                // ✅ Modificación 2: Lógica genérica para actualizar/crear balances de criptos
                // Se usa el nuevo campo 'amount' y 'cryptoSymbol' del DTO
                accountBinanceService.updateOrCreateCryptoBalance(account.getId(), dto.getCryptoSymbol(), dto.getAmount());
                
                BuyDollars nueva = new BuyDollars();
                nueva.setIdDeposit(dto.getIdDeposit());
                nueva.setNameAccount(dto.getNameAccount());
                nueva.setDate(dto.getDate());
                // Guardar en escala "miles" (/1000); el saldo cripto de arriba ya usó el monto crudo.
                nueva.setAmount(dto.getAmount() == null ? null : dto.getAmount() / 1000.0);
                nueva.setCryptoSymbol(dto.getCryptoSymbol());
                nueva.setTasa(0.0);
                nueva.setPesos(0.0);
                nueva.setAsignada(false);
                nueva.setAccountBinance(account);
                nueva.setDedupeKey(dedupeKey);
                nueva.setTxId(dto.getTxId());
                buyDollarsRepository.save(nueva);
                existentes.add(dto.getIdDeposit());
            }

        } catch (Exception e) {
            throw new RuntimeException("Error al registrar compras automáticamente", e);
        }
    }
	
	@Override
    public List<BuyDollarsDto> getComprasNoAsignadasHoy() {
        ZoneId zoneId = ZoneId.of("America/Bogota");
        LocalDateTime start = LocalDate.now(zoneId).atStartOfDay();
        LocalDateTime end = LocalDate.now(zoneId).atTime(LocalTime.MAX);
        List<BuyDollars> compras = buyDollarsRepository.findNoAsignadasHoy(start, end);

        return compras.stream().map(buy -> {
            BuyDollarsDto dto = new BuyDollarsDto();
            dto.setId(buy.getId());
            // ✅ Uso del nuevo campo 'amount' en lugar de 'dollars'
            dto.setAmount(buy.getAmount());
            // ✅ Uso del nuevo campo 'cryptoSymbol'
            dto.setCryptoSymbol(buy.getCryptoSymbol());
            dto.setTasa(buy.getTasa());
            dto.setNameAccount(buy.getNameAccount());
            dto.setDate(buy.getDate());
            dto.setIdDeposit(buy.getIdDeposit());
            dto.setPesos(buy.getPesos());
            dto.setAccountBinanceId(buy.getAccountBinance().getId());
            dto.setAsignada(buy.getAsignada());
            return dto;
        }).collect(Collectors.toList());
    }

	@Override
	@Transactional
	public BuyDollars asignarCompra(Integer id, BuyDollarsDto dto) {
	    BuyDollars existing = buyDollarsRepository.findById(id)
	        .orElseThrow(() -> new RuntimeException("Compra no encontrada"));

	    if (Boolean.TRUE.equals(existing.getAsignada())) {
	        throw new RuntimeException("Compra ya fue asignada");
	    }

	    // === "Monto verdadero" (opcional) ===
	    // Por temas de comisiones de red, a veces llegan p.ej. 29.998 en vez de 30.
	    // Si el operario informa el monto real que se compró, se guarda ese monto
	    // (en la misma escala "miles" que amount) para que el paso a pesos sea exacto.
	    // El saldo cripto NO se ajusta: refleja el USDT realmente recibido.
	    if (dto.getMontoVerdadero() != null && dto.getMontoVerdadero() > 0) {
	        existing.setAmount(dto.getMontoVerdadero());
	    }

	    // === Actualizar datos de la compra ===
	    existing.setTasa(dto.getTasa());
	    Double montoUsdt = existing.getAmount();
	    Double montoPesos = montoUsdt * dto.getTasa();
	    existing.setPesos(montoPesos);

	    // === NUEVA LÓGICA DE TASA PROMEDIO POR DÍA ===
	    AverageRate tasaDia = averageRateService.actualizarTasaPromedioPorCompra(
	            existing.getDate(),
	            montoUsdt,
	            dto.getTasa()
	    );

	    // (Opcional) si quieres loguear o devolver info:
	    // Double tasaPromedioActual = tasaDia.getAverageRate();

	    // --- ASIGNACIÓN DINÁMICA (igual que antes) ---
	    if (dto.getSupplierId() != null) {
	        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
	            .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
	        existing.setSupplier(supplier);
	        existing.setCliente(null);

	        Double currentBalance = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
	        supplier.setBalance(currentBalance + montoPesos);
	        supplierRepository.save(supplier);

	    } else if (dto.getClienteId() != null) {
	        Cliente cliente = clienteRepository.findById(dto.getClienteId())
	            .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
	        existing.setCliente(cliente);
	        existing.setSupplier(null);

	        Double currentSaldo = cliente.getSaldo() != null ? cliente.getSaldo() : 0.0;
	        cliente.setSaldo(currentSaldo + montoPesos);
	        clienteRepository.save(cliente);

	    } else {
	        throw new RuntimeException("Debe especificar un proveedor o un cliente para la asignación");
	    }

	    existing.setAsignada(true);

	    return buyDollarsRepository.save(existing);
	}



	@Override
	public BuyDollars createBuyDollars(BuyDollarsDto buyDollarsDto) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<BuyDollarsDto> listarComprasPorCliente(Integer clienteId) {
	    if (clienteId == null) throw new IllegalArgumentException("clienteId no puede ser nulo");

	    // Usa el ordenado si lo agregaste; si no, usa findByCliente_Id
	    List<BuyDollars> compras = buyDollarsRepository.findByCliente_IdOrderByDateDesc(clienteId);

	    return compras.stream()
	        .map(this::toDto)
	        .collect(Collectors.toList());
	}
	
	
	@Override
	public List<BuyDollarsDto> listarComprasPorProveedor(Integer proveedorId) {
		if(proveedorId == null) throw new IllegalArgumentException("proveedorId no puede ser nulo");
		
		List<BuyDollars> compras = buyDollarsRepository.findBySupplier_IdOrderByDateDesc(proveedorId);
		
		return compras.stream().map(this::toDto).collect(Collectors.toList());
	}
	
	
	
	private BuyDollarsDto toDto(BuyDollars b) {
	    BuyDollarsDto dto = new BuyDollarsDto();
	    dto.setId(b.getId());
	    dto.setTasa(b.getTasa());
	    dto.setAmount(b.getAmount());
	    dto.setCryptoSymbol(b.getCryptoSymbol());
	    dto.setPesos(b.getPesos());
	    dto.setDate(b.getDate());
	    dto.setNameAccount(b.getNameAccount());
	    dto.setIdDeposit(b.getIdDeposit());
	    dto.setAsignada(b.getAsignada());
	    dto.setTxId(b.getTxId());

	    // Campos relacionales (con null-safety)
	    if (b.getCliente() != null) dto.setClienteId(b.getCliente().getId());
	    if (b.getSupplier() != null) dto.setSupplierId(b.getSupplier().getId());
	    if (b.getAccountBinance() != null) dto.setAccountBinanceId(b.getAccountBinance().getId());

	    return dto;
	}

	private String buildDedupeKey(BuyDollarsDto dto) {
		  if (dto.getIdDeposit() != null && !dto.getIdDeposit().isBlank()) {
		    return dto.getIdDeposit().trim().toUpperCase();
		  }
		  // Fallback determinístico
		  String base = String.join("|",
		      // Si tienes dto.getFuente() úsalo, si no "GEN"
		      "GEN",
		      String.valueOf(dto.getNameAccount()).trim().toUpperCase(),
		      String.valueOf(dto.getCryptoSymbol()).trim().toUpperCase(),
		      String.valueOf(dto.getAmount()),
		      // Trunca a minutos para reducir jitter de timestamps
		      String.valueOf(dto.getDate().truncatedTo(ChronoUnit.MINUTES))
		  );
		  return DigestUtils.sha256Hex(base);
		}
	
	@Override
	public List<BuyDollars> obtenerComprasNoAsignadasPorFecha(LocalDate fecha) {
	    LocalDateTime start = fecha.atStartOfDay();
	    LocalDateTime end   = fecha.plusDays(1).atStartOfDay();
	    return buyDollarsRepository
	            .findByAsignadaFalseAndDateBetween(start, end);
	}

	@Override
	public List<BuyDollarsDto> getComprasNoAsignadas() {
	    List<BuyDollars> compras = buyDollarsRepository.findByAsignadaFalseOrderByDateDesc();

	    return compras.stream().map(buy -> {
	        BuyDollarsDto dto = new BuyDollarsDto();
	        dto.setId(buy.getId());
	        dto.setAmount(buy.getAmount());
	        dto.setCryptoSymbol(buy.getCryptoSymbol());
	        dto.setTasa(buy.getTasa());
	        dto.setNameAccount(buy.getNameAccount());
	        dto.setDate(buy.getDate());
	        dto.setIdDeposit(buy.getIdDeposit());
	        dto.setPesos(buy.getPesos());
	        dto.setAccountBinanceId(buy.getAccountBinance().getId());
	        dto.setAsignada(buy.getAsignada());
	        return dto;
	    }).collect(Collectors.toList());
	}
	
	@Override
    public List<BuyDollars> obtenerComprasNoAsignadas() {
        // si quieres ordenadas:
        return buyDollarsRepository.findByAsignadaFalseOrderByDateDesc();

        // o sin orden:
        // return buyDollarsRepository.findByAsignadaFalse();
    }

}
