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
	private SolanaController solanaController;

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

	        // Valores antiguos
	        Double oldAmount = existing.getAmount();
	        Double oldPesos = existing.getPesos();
	        Supplier oldSupplier = existing.getSupplier();
	        AccountBinance oldAccount = existing.getAccountBinance();
	        String oldCryptoSymbol = existing.getCryptoSymbol();
	        
	        // Valores nuevos
	        Double newAmount = dto.getAmount();
	        Double newTasa = dto.getTasa();
	        Double newPesos = newAmount * newTasa;
	        Integer newSupplierId = dto.getSupplierId();
	        Integer newAccountId = dto.getAccountBinanceId();
	        String newCryptoSymbol = dto.getCryptoSymbol();

	        // 1. Revertir saldos anteriores
	        if (oldSupplier != null) {
	            oldSupplier.setBalance(oldSupplier.getBalance() - oldPesos);
	            supplierRepository.save(oldSupplier);
	        }
	        if (oldAccount != null) {
	            accountBinanceService.subtractCryptoBalance(oldAccount.getId(), oldCryptoSymbol, oldAmount);
	        }

	        // 2. Actualizar la entidad BuyDollars
	        existing.setAmount(newAmount);
	        existing.setTasa(newTasa);
	        existing.setPesos(newPesos);
	        existing.setCryptoSymbol(newCryptoSymbol);
	        
	        // Asignar proveedor
	        Supplier newSupplier = supplierRepository.findById(newSupplierId)
	            .orElseThrow(() -> new RuntimeException("Nuevo proveedor no encontrado"));
	        existing.setSupplier(newSupplier);

	        // Asignar cuenta de Binance
	        AccountBinance newAccount = accountBinanceRepository.findById(newAccountId)
	            .orElseThrow(() -> new RuntimeException("Nueva cuenta de Binance no encontrada"));
	        existing.setAccountBinance(newAccount);
	        
	        // 3. Aplicar los nuevos saldos
	        Double currentNewSupplierBalance = newSupplier.getBalance() != null ? newSupplier.getBalance() : 0.0;
	        newSupplier.setBalance(currentNewSupplierBalance + newPesos);
	        supplierRepository.save(newSupplier);
	        
	        accountBinanceService.updateOrCreateCryptoBalance(newAccount.getId(), newCryptoSymbol, newAmount);
	        
	        // Se guarda la compra con los nuevos datos
	        return buyDollarsRepository.save(existing);
	    }

	@Override
    public void registrarComprasAutomaticamente() {
        try {
            // ✅ Modificación 1: Los controladores devuelven una lista de DTOs genéricos
            List<BuyDollarsDto> binancePay = binancePayController.getComprasNoRegistradas().getBody();
            List<BuyDollarsDto> spot = spotOrdersController.getComprasNoRegistradas(20).getBody();
            List<BuyDollarsDto> trust = tronScanController.getUSDTIncomingTransfers().getBody();
           // List<BuyDollarsDto> sol = solanaController.getSolanaIncomingTransfers().getBody(); miton dijo que no interesaba las entradas de solana 
            Set<String> existentes = buyDollarsRepository.findAll().stream()
                .map(BuyDollars::getIdDeposit)
                .collect(Collectors.toSet());

            List<BuyDollarsDto> todas = new ArrayList<>();
            if (binancePay != null) todas.addAll(binancePay);
            if (spot != null) todas.addAll(spot);
            if (trust != null) todas.addAll(trust);
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

                // ✅ Modificación 2: Lógica genérica para actualizar/crear balances de criptos
                // Se usa el nuevo campo 'amount' y 'cryptoSymbol' del DTO
                accountBinanceService.updateOrCreateCryptoBalance(account.getId(), dto.getCryptoSymbol(), dto.getAmount());
                
                BuyDollars nueva = new BuyDollars();
                nueva.setIdDeposit(dto.getIdDeposit());
                nueva.setNameAccount(dto.getNameAccount());
                nueva.setDate(dto.getDate());
                nueva.setAmount(dto.getAmount());
                nueva.setCryptoSymbol(dto.getCryptoSymbol());
                nueva.setTasa(0.0);
                nueva.setPesos(0.0);
                nueva.setAsignada(false);
                nueva.setAccountBinance(account);
                nueva.setDedupeKey(dedupeKey);
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
		      // Si tienes dto.getFuente() úsalo, si no “GEN”
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


}
