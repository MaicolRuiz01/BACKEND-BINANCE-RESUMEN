package com.binance.web.movimientos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.Movimiento;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.model.AjusteSaldoDto;
import com.binance.web.model.PagoClienteAClienteDto;
import com.binance.web.model.PagoClienteAProveedorDto;
import com.binance.web.model.PagoProveedorAClienteDto;
import com.binance.web.movimientos.MovimientoDTO;
import com.binance.web.util.CupoDiarioRules;

import jakarta.transaction.Transactional;


@Service
public class MovimientoServiceImplement implements MovimientoService {

	@Autowired
	private MovimientoRepository movimientoRepository;
	@Autowired
	private AccountCopRepository accountCopRepository;
	@Autowired
	private EfectivoRepository efectivoRepository;
	@Autowired
	private ClienteRepository clienteRepository;
	@Autowired
	private SupplierRepository supplierRepository;
	private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

	@Override
	public Movimiento RegistrarTransferencia(Integer idCuentoFrom, Integer idCuentaTo, Double monto) {
		AccountCop cuentaFrom = accountCopRepository.findById(idCuentoFrom)
				.orElseThrow(() -> new RuntimeException("Cuenta de Origen no encontrada"));
		AccountCop cuentaTo = accountCopRepository.findById(idCuentaTo)
				.orElseThrow(() -> new RuntimeException("Cuenta Destino no encontrada"));

		double comision = monto * 0.004;
		// 4x1000: diferido en BANCOLOMBIA (scheduler al día siguiente), inmediato en los demás.
		boolean esBanco = "BANCOLOMBIA".equalsIgnoreCase(String.valueOf(cuentaFrom.getBankType()));
		double deduccionHoy = esBanco ? monto : (monto + comision);

		Movimiento mov = Movimiento.builder().tipo("TRANSFERENCIA").fecha(LocalDateTime.now()).monto(monto)
				.cuentaOrigen(cuentaFrom) // ✅ origen correcto
				.cuentaDestino(cuentaTo) // ✅ destino correcto
				.comision(comision) // ✅ sólo la comisión, no "monto+comisión"
				.comisionAplicada(!esBanco) // Bancolombia: 4x1000 pendiente (diferido)
				.build();

		cuentaFrom.setBalance(cuentaFrom.getBalance() - deduccionHoy);
		cuentaTo.setBalance(cuentaTo.getBalance() + monto);
		accountCopRepository.save(cuentaFrom);
		accountCopRepository.save(cuentaTo);

		return movimientoRepository.save(mov);
	}

	@Override
	@Transactional
	public Movimiento RegistrarTransferenciaCaja(Integer cajaFromId, Integer cajaToId, Double monto) {
		if (cajaFromId == null || cajaToId == null) {
			throw new IllegalArgumentException("Debe indicar caja origen y caja destino.");
		}
		if (cajaFromId.equals(cajaToId)) {
			throw new IllegalArgumentException("La caja origen y destino no pueden ser la misma.");
		}
		if (monto == null || monto <= 0) {
			throw new IllegalArgumentException("El monto debe ser mayor a 0.");
		}

		Efectivo origen = efectivoRepository.findById(cajaFromId)
				.orElseThrow(() -> new RuntimeException("Caja origen no encontrada"));
		Efectivo destino = efectivoRepository.findById(cajaToId)
				.orElseThrow(() -> new RuntimeException("Caja destino no encontrada"));

		double saldoOrigen = origen.getSaldo() != null ? origen.getSaldo() : 0.0;
		if (saldoOrigen < monto) {
			throw new IllegalArgumentException("Saldo insuficiente en la caja origen.");
		}

		// SIN 4x1000: se mueve el monto exacto.
		origen.setSaldo(saldoOrigen - monto);
		destino.setSaldo((destino.getSaldo() != null ? destino.getSaldo() : 0.0) + monto);
		efectivoRepository.save(origen);
		efectivoRepository.save(destino);

		Movimiento mov = Movimiento.builder()
				.tipo("TRANSFERENCIA CAJA")
				.fecha(LocalDateTime.now())
				.monto(monto)
				.caja(origen)          // caja origen
				.cajaDestino(destino)  // caja destino
				.comision(0.0)
				.saldoCajaResultante(origen.getSaldo())
				.saldoCajaDestinoResultante(destino.getSaldo())
				.build();

		return movimientoRepository.save(mov);
	}
	
	/** Delegado al util compartido — así el mismo reset diario lo usan movimientos
	 *  directos y el módulo de retiradores, sin duplicar la lógica. */
	private void asegurarCupoHoy(AccountCop acc) {
	    CupoDiarioRules.asegurarCupoHoy(acc);
	}

	@Override
	@Transactional
	public Movimiento RegistrarRetiro(Integer cuentaId, Integer cajaId, Double monto, String tipoRetiro) {

	    if (monto == null || monto <= 0) {
	        throw new IllegalArgumentException("Monto debe ser > 0");
	    }

	    boolean esCajero = "CAJERO".equalsIgnoreCase(tipoRetiro);
	    boolean esCorresponsal = "CORRESPONSAL".equalsIgnoreCase(tipoRetiro);
	    if (!esCajero && !esCorresponsal) {
	        throw new IllegalArgumentException("tipoRetiro debe ser CAJERO o CORRESPONSAL");
	    }

	    // 🔒 lock para que retiros simultáneos no se "salten" el cupo
	    AccountCop cuentaOrigen = accountCopRepository.findByIdForUpdate(cuentaId)
	            .orElseThrow(() -> new RuntimeException("Cuenta de Origen no encontrada"));

	    Efectivo caja = efectivoRepository.findById(cajaId)
	            .orElseThrow(() -> new RuntimeException("Caja no encontrada"));

	    // Inicializar/resetear cupos del día
	    asegurarCupoHoy(cuentaOrigen);

	    // Validar y descontar el cupo correspondiente al tipo elegido
	    if (esCajero) {
	        double disponible = cuentaOrigen.getCupoCajeroDisponibleHoy() != null ? cuentaOrigen.getCupoCajeroDisponibleHoy() : 0.0;
	        if (monto > disponible) {
	            throw new IllegalArgumentException(
	                "Cupo de cajero diario insuficiente. Disponible: $" + String.format("%,.0f", disponible) + " · Requerido: $" + String.format("%,.0f", monto));
	        }
	        cuentaOrigen.setCupoCajeroDisponibleHoy(round2(disponible - monto));
	    } else {
	        double disponible = cuentaOrigen.getCupoCorresponsalDisponibleHoy() != null ? cuentaOrigen.getCupoCorresponsalDisponibleHoy() : 0.0;
	        if (monto > disponible) {
	            throw new IllegalArgumentException(
	                "Cupo de corresponsal diario insuficiente. Disponible: $" + String.format("%,.0f", disponible) + " · Requerido: $" + String.format("%,.0f", monto));
	        }
	        cuentaOrigen.setCupoCorresponsalDisponibleHoy(round2(disponible - monto));
	    }
	    // Mantener el campo legacy sincronizado
	    double cajeroRest      = cuentaOrigen.getCupoCajeroDisponibleHoy()      != null ? cuentaOrigen.getCupoCajeroDisponibleHoy()      : 0.0;
	    double corresponsalRest = cuentaOrigen.getCupoCorresponsalDisponibleHoy() != null ? cuentaOrigen.getCupoCorresponsalDisponibleHoy() : 0.0;
	    cuentaOrigen.setCupoDisponibleHoy(round2(cajeroRest + corresponsalRest));

	    // Comisión 4x1000. En BANCOLOMBIA se difiere al día siguiente (la aplica el scheduler);
	    // en Nequi/Daviplata se descuenta al instante.
	    double comision = monto * 0.004;
	    double montoConComision = monto + comision;
	    double saldoCuenta = cuentaOrigen.getBalance() != null ? cuentaOrigen.getBalance() : 0.0;
	    boolean diferir4x1000 = "BANCOLOMBIA".equalsIgnoreCase(String.valueOf(cuentaOrigen.getBankType()));

	    // Lo que realmente sale HOY de la cuenta (Bancolombia solo el monto; el 4x1000 va mañana).
	    double deduccionHoy = diferir4x1000 ? monto : montoConComision;
	    if (deduccionHoy > saldoCuenta) {
	        throw new IllegalArgumentException("Saldo insuficiente. Disponible: $" + String.format("%,.0f", saldoCuenta) + " · Requerido: $" + String.format("%,.0f", deduccionHoy));
	    }

	    Movimiento mov = Movimiento.builder()
	            .tipo("RETIRO " + tipoRetiro.toUpperCase())
	            .fecha(LocalDateTime.now())
	            .monto(monto)
	            .cuentaOrigen(cuentaOrigen)
	            .caja(caja)
	            .comision(comision)
	            // Bancolombia: 4x1000 pendiente (se aplica al día siguiente). Otros bancos: ya aplicado.
	            .comisionAplicada(!diferir4x1000)
	            .build();

	    cuentaOrigen.setBalance(round2(saldoCuenta - deduccionHoy));

	    double saldoCaja = caja.getSaldo() != null ? caja.getSaldo() : 0.0;
	    caja.setSaldo(round2(saldoCaja + monto));
	    mov.setSaldoCajaResultante(caja.getSaldo());

	    accountCopRepository.save(cuentaOrigen);
	    efectivoRepository.save(caja);

	    return movimientoRepository.save(mov);
	}


	@Override
	public Movimiento RegistrarDeposito(Integer cuentaId, Integer cajaId, Double monto) {
		AccountCop cuentaDestino = accountCopRepository.findById(cuentaId).orElseThrow();
		Efectivo caja = efectivoRepository.findById(cajaId).orElseThrow();

		Movimiento mov = Movimiento.builder().tipo("DEPOSITO").fecha(LocalDateTime.now()).monto(monto)
				.cuentaDestino(cuentaDestino).caja(caja).comision(0.0).build();

		cuentaDestino.setBalance(cuentaDestino.getBalance() + monto);
		caja.setSaldo(caja.getSaldo() - monto);
		mov.setSaldoCajaResultante(caja.getSaldo());
		accountCopRepository.save(cuentaDestino);
		efectivoRepository.save(caja);

		return movimientoRepository.save(mov);
	}

	public Movimiento registrarPagoCliente(Integer cuentaId, Integer clienteId, Double monto) {
		AccountCop cuentaDestino = accountCopRepository.findById(cuentaId).orElseThrow();
		Cliente cliente = clienteRepository.findById(clienteId).orElseThrow();

		cliente.setSaldo((cliente.getSaldo() != null ? cliente.getSaldo() : 0) + monto);
		cuentaDestino.setBalance((cuentaDestino.getBalance() != null ? cuentaDestino.getBalance() : 0) + monto);

		Movimiento mov = Movimiento.builder().tipo("PAGO").fecha(LocalDateTime.now()).monto(monto)
				.cuentaDestino(cuentaDestino).pagoCliente(cliente).comision(0.0).build();

		accountCopRepository.save(cuentaDestino);
		clienteRepository.save(cliente);
		return movimientoRepository.save(mov);
	}

	@Override
	@Transactional
	public Movimiento registrarPagoProveedor(Integer cuentaCopId, Integer cajaId, Integer proveedorOrigenId,
			Integer proveedorDestinoId, Integer clienteId, Double monto) {

		if (cuentaCopId == null && cajaId == null && proveedorOrigenId == null && clienteId == null) {
			throw new IllegalArgumentException("Debe proporcionar una cuenta, una caja o un proveedor para el pago.");
		}

		Supplier supplier = supplierRepository.findById(proveedorDestinoId)
				.orElseThrow(() -> new RuntimeException("Proveedor no encontrado."));

		Movimiento pagoProveedor = new Movimiento();
		double comision = 0.0;
		// Por defecto no hay comisión pendiente (pagos desde caja/proveedor/cliente no
		// tienen 4x1000). La rama de cuentaCopId la pisa según el banco si aplica.
		pagoProveedor.setComisionAplicada(true);

		if (cuentaCopId != null) {
			AccountCop cuentaCop = accountCopRepository.findById(cuentaCopId)
					.orElseThrow(() -> new RuntimeException("Cuenta de Origen no encontrada."));

			comision = monto * 0.004;
			// 4x1000: diferido en BANCOLOMBIA (lo cobra el scheduler al día siguiente),
			// inmediato en los demás bancos.
			boolean esBanco = "BANCOLOMBIA".equalsIgnoreCase(String.valueOf(cuentaCop.getBankType()));
			double deduccionHoy = esBanco ? monto : (monto + comision);

			// Actualizar el balance de la cuenta
			cuentaCop.setBalance(cuentaCop.getBalance() - deduccionHoy);
			accountCopRepository.save(cuentaCop);

			// Crear el objeto Movimiento
			pagoProveedor.setCuentaOrigen(cuentaCop);
			pagoProveedor.setComision(comision);
			pagoProveedor.setComisionAplicada(!esBanco);

		}
		// 4. Lógica para el pago desde la caja (efectivo)
		else if (cajaId != null) {
			Efectivo caja = efectivoRepository.findById(cajaId)
					.orElseThrow(() -> new RuntimeException("Caja no encontrada."));

			// Actualizar el saldo de la caja
			caja.setSaldo(caja.getSaldo() - monto);
			efectivoRepository.save(caja);

			// Crear el objeto Movimiento
			pagoProveedor.setCaja(caja);
			pagoProveedor.setSaldoCajaResultante(caja.getSaldo());
			pagoProveedor.setComision(0.0); // No hay comisión por pagos en efectivo
		} else if (proveedorOrigenId != null) {
			Supplier proveedorOrigen = supplierRepository.findById(proveedorOrigenId)
					.orElseThrow(() -> new RuntimeException("Proveedor Origen no encontrado"));
			proveedorOrigen.setBalance(proveedorOrigen.getBalance() + monto);
			supplierRepository.save(proveedorOrigen);
			pagoProveedor.setProveedorOrigen(proveedorOrigen);
		} else if (clienteId != null) {
			Cliente cliente = clienteRepository.findById(clienteId)
					.orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
			cliente.setSaldo(cliente.getSaldo() + monto);
			clienteRepository.save(cliente);
			pagoProveedor.setPagoCliente(cliente);
		}

		// 5. Lógica común para ambos casos
		// Actualizar el balance del proveedor (¡ahora sí se ejecuta!)
		supplier.setBalance(supplier.getBalance() - monto);
		supplierRepository.save(supplier);

		// Llenar los datos restantes del Movimiento
		pagoProveedor.setTipo("PAGO PROVEEDOR");
		pagoProveedor.setFecha(LocalDateTime.now());
		pagoProveedor.setMonto(monto);
		pagoProveedor.setPagoProveedor(supplier);

		// 6. Guardar el movimiento
		return movimientoRepository.save(pagoProveedor);
	}

	/**
	 * El proveedor nos DA efectivo → ENTRA a una caja. Sin 4x1000 (es efectivo).
	 * El saldo del proveedor es un "debe/debemos": si nos frontea efectivo, le debemos MÁS
	 * (misma convención que proveedorOrigen en los pagos: += monto).
	 */
	@Override
	@Transactional
	public Movimiento registrarPrestamoProveedorACaja(Integer proveedorId, Integer cajaId, Double monto) {
		if (proveedorId == null || cajaId == null)
			throw new IllegalArgumentException("Debe indicar el proveedor y la caja.");
		if (monto == null || monto <= 0)
			throw new IllegalArgumentException("El monto debe ser mayor a 0.");

		Supplier proveedor = supplierRepository.findById(proveedorId)
				.orElseThrow(() -> new RuntimeException("Proveedor no encontrado."));
		Efectivo caja = efectivoRepository.findById(cajaId)
				.orElseThrow(() -> new RuntimeException("Caja no encontrada."));

		// Entra a la caja (sin comisión).
		caja.setSaldo((caja.getSaldo() != null ? caja.getSaldo() : 0.0) + monto);
		efectivoRepository.save(caja);

		// Es un PRÉSTAMO: el proveedor nos presta efectivo → le debemos MÁS → su saldo AUMENTA (debe/debemos).
		proveedor.setBalance((proveedor.getBalance() != null ? proveedor.getBalance() : 0.0) + monto);
		supplierRepository.save(proveedor);

		Movimiento mov = new Movimiento();
		mov.setTipo("PRESTAMO PROVEEDOR A CAJA");
		mov.setFecha(LocalDateTime.now());
		mov.setMonto(monto);
		mov.setComision(0.0);
		mov.setCaja(caja);                 // entra a esta caja
		mov.setProveedorOrigen(proveedor); // el proveedor es el origen del dinero
		mov.setSaldoCajaResultante(caja.getSaldo());
		return movimientoRepository.save(mov);
	}

	/**
	 * El CLIENTE nos PRESTA efectivo → ENTRA a una caja. Sin 4x1000 (es efectivo).
	 * El saldo del cliente es un "debe/debemos": si nos presta efectivo, le debemos MÁS (+= monto).
	 */
	@Override
	@Transactional
	public Movimiento registrarPrestamoClienteACaja(Integer clienteId, Integer cajaId, Double monto) {
		if (clienteId == null || cajaId == null)
			throw new IllegalArgumentException("Debe indicar el cliente y la caja.");
		if (monto == null || monto <= 0)
			throw new IllegalArgumentException("El monto debe ser mayor a 0.");

		Cliente cliente = clienteRepository.findById(clienteId)
				.orElseThrow(() -> new RuntimeException("Cliente no encontrado."));
		Efectivo caja = efectivoRepository.findById(cajaId)
				.orElseThrow(() -> new RuntimeException("Caja no encontrada."));

		// Entra a la caja (sin comisión).
		caja.setSaldo((caja.getSaldo() != null ? caja.getSaldo() : 0.0) + monto);
		efectivoRepository.save(caja);

		// Es un PRÉSTAMO: el cliente nos presta efectivo → le debemos MÁS → su saldo AUMENTA (debe/debemos).
		cliente.setSaldo((cliente.getSaldo() != null ? cliente.getSaldo() : 0.0) + monto);
		clienteRepository.save(cliente);

		Movimiento mov = new Movimiento();
		mov.setTipo("PRESTAMO CLIENTE A CAJA");
		mov.setFecha(LocalDateTime.now());
		mov.setMonto(monto);
		mov.setComision(0.0);
		mov.setCaja(caja);                 // entra a esta caja
		mov.setClienteOrigen(cliente);     // el cliente es el origen del dinero
		mov.setSaldoCajaResultante(caja.getSaldo());
		return movimientoRepository.save(mov);
	}

	@Override
	public List<Movimiento> listar() {
		// TODO Auto-generated method stub
		return movimientoRepository.findAll();
	}

	@Override
	public List<Movimiento> listarRetiros() {
		// Los retiros se guardan como "RETIRO CAJERO" / "RETIRO CORRESPONSAL",
		// así que hay que buscar por prefijo (findByTipo exacto los perdía).
		return movimientoRepository.findByTipoStartingWithOrderByFechaDesc("RETIRO");
	}

	@Override
	public List<Movimiento> listarDepositos() {
		return movimientoRepository.findByTipo("DEPOSITO");
	}

	@Override
	public List<Movimiento> listarTransferencias() {
		return movimientoRepository.findByTipo("TRANSFERENCIA");
	}

	@Override
	public List<Movimiento> listarPagos() {
		return movimientoRepository.findByTipo("PAGO");
	}

	@Override
	@Transactional
	public Movimiento actualizarMovimiento(Integer id, Double montoParam, Integer cuentaOrigenId, Integer cuentaDestinoId,
			Integer cajaId) {
		Movimiento m = movimientoRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Movimiento no encontrado con id: " + id));

		String tipo = m.getTipo() != null ? m.getTipo() : "";

		// Solo se puede editar lo que sabemos revertir/aplicar sobre saldos (igual que eliminarMovimiento).
		boolean soportado = "PAGO PROVEEDOR".equals(tipo)
				|| tipo.startsWith("RETIRO")
				|| "TRANSFERENCIA CAJA".equals(tipo);
		if (!soportado) {
			throw new IllegalStateException(
				"Este tipo de movimiento aún no se puede editar (solo PAGO PROVEEDOR, RETIRO y TRANSFERENCIA CAJA).");
		}

		double montoViejo    = m.getMonto() != null ? m.getMonto() : 0.0;
		double comisionVieja = m.getComision() != null ? m.getComision() : 0.0;

		// Histórico de caja: snapshot ANTES de tocar nada, para poder calcular el
		// delta exacto que hay que propagar hacia adelante en el tiempo.
		Efectivo cajaOrigenAntes = m.getCaja();
		Efectivo cajaDestinoAntes = m.getCajaDestino();
		Double saldoCajaOrigenAntes = cajaOrigenAntes != null ? cajaOrigenAntes.getSaldo() : null;
		Double saldoCajaDestinoAntes = cajaDestinoAntes != null ? cajaDestinoAntes.getSaldo() : null;

		// ── 1) REVERTIR el efecto viejo sobre los saldos (mismo criterio que eliminarMovimiento) ──
		if ("PAGO PROVEEDOR".equals(tipo)) {
			Supplier destino = m.getPagoProveedor();
			if (destino != null) {
				destino.setBalance((destino.getBalance() != null ? destino.getBalance() : 0.0) + montoViejo);
				supplierRepository.save(destino);
			}
			if (m.getCuentaOrigen() != null) {
				// Solo devolver el 4x1000 si ya se había descontado (Bancolombia diferido = aún no).
				boolean comisionViejaAplicada = !Boolean.FALSE.equals(m.getComisionAplicada());
				double aReversar = montoViejo + (comisionViejaAplicada ? comisionVieja : 0.0);
				AccountCop c = m.getCuentaOrigen();
				c.setBalance(round2((c.getBalance() != null ? c.getBalance() : 0.0) + aReversar));
				accountCopRepository.save(c);
			} else if (m.getCaja() != null) {
				Efectivo caja = m.getCaja();
				caja.setSaldo(round2((caja.getSaldo() != null ? caja.getSaldo() : 0.0) + montoViejo));
				efectivoRepository.save(caja);
			} else if (m.getProveedorOrigen() != null) {
				Supplier po = m.getProveedorOrigen();
				po.setBalance((po.getBalance() != null ? po.getBalance() : 0.0) - montoViejo);
				supplierRepository.save(po);
			} else if (m.getPagoCliente() != null) {
				Cliente cl = m.getPagoCliente();
				cl.setSaldo((cl.getSaldo() != null ? cl.getSaldo() : 0.0) - montoViejo);
				clienteRepository.save(cl);
			}
		} else if (tipo.startsWith("RETIRO")) {
			AccountCop cuenta = m.getCuentaOrigen();
			Efectivo caja = m.getCaja();
			boolean comisionYaAplicada = !Boolean.FALSE.equals(m.getComisionAplicada());
			double aReversar = montoViejo + (comisionYaAplicada ? comisionVieja : 0.0);
			if (cuenta != null) {
				cuenta.setBalance(round2((cuenta.getBalance() != null ? cuenta.getBalance() : 0.0) + aReversar));
				asegurarCupoHoy(cuenta);
				java.time.LocalDate diaRetiro = m.getFecha() != null ? m.getFecha().toLocalDate() : null;
				java.time.LocalDate hoy = java.time.LocalDate.now(ZONE_BOGOTA);
				if (diaRetiro != null && diaRetiro.equals(hoy) && cuenta.getBankType() != null) {
					if (tipo.contains("CAJERO")) {
						double max = CupoDiarioRules.maxCajeroPorBanco(cuenta.getBankType());
						double disp = cuenta.getCupoCajeroDisponibleHoy() != null ? cuenta.getCupoCajeroDisponibleHoy() : 0.0;
						cuenta.setCupoCajeroDisponibleHoy(round2(Math.min(max, disp + montoViejo)));
					} else if (tipo.contains("CORRESPONSAL")) {
						double max = CupoDiarioRules.maxCorresponsalPorBanco(cuenta.getBankType());
						double disp = cuenta.getCupoCorresponsalDisponibleHoy() != null ? cuenta.getCupoCorresponsalDisponibleHoy() : 0.0;
						cuenta.setCupoCorresponsalDisponibleHoy(round2(Math.min(max, disp + montoViejo)));
					}
					double cj = cuenta.getCupoCajeroDisponibleHoy() != null ? cuenta.getCupoCajeroDisponibleHoy() : 0.0;
					double co = cuenta.getCupoCorresponsalDisponibleHoy() != null ? cuenta.getCupoCorresponsalDisponibleHoy() : 0.0;
					cuenta.setCupoDisponibleHoy(round2(cj + co));
				}
				accountCopRepository.save(cuenta);
			}
			if (caja != null) {
				caja.setSaldo(round2((caja.getSaldo() != null ? caja.getSaldo() : 0.0) - montoViejo));
				efectivoRepository.save(caja);
			}
		} else if ("TRANSFERENCIA CAJA".equals(tipo)) {
			Efectivo origen = m.getCaja();
			Efectivo destino = m.getCajaDestino();
			if (origen != null) {
				origen.setSaldo(round2((origen.getSaldo() != null ? origen.getSaldo() : 0.0) + montoViejo));
				efectivoRepository.save(origen);
			}
			if (destino != null) {
				destino.setSaldo(round2((destino.getSaldo() != null ? destino.getSaldo() : 0.0) - montoViejo));
				efectivoRepository.save(destino);
			}
		}

		// ── 2) Actualizar referencias y monto del movimiento (nuevo estado) ──
		if (cuentaOrigenId != null) {
			AccountCop cuentaOrigen = accountCopRepository.findById(cuentaOrigenId)
					.orElseThrow(() -> new RuntimeException("Cuenta origen no encontrada con id: " + cuentaOrigenId));
			m.setCuentaOrigen(cuentaOrigen);
		}
		if (cuentaDestinoId != null) {
			AccountCop cuentaDestino = accountCopRepository.findById(cuentaDestinoId)
					.orElseThrow(() -> new RuntimeException("Cuenta destino no encontrada con id: " + cuentaDestinoId));
			m.setCuentaDestino(cuentaDestino);
		}
		if (cajaId != null) {
			Efectivo caja = efectivoRepository.findById(cajaId)
					.orElseThrow(() -> new RuntimeException("Caja no encontrada con id: " + cajaId));
			m.setCaja(caja);
		}

		double montoNuevo = montoParam != null ? montoParam : montoViejo;
		m.setMonto(montoNuevo);

		// ── 3) APLICAR el efecto nuevo sobre los saldos (mismo criterio que al crear) ──
		if ("PAGO PROVEEDOR".equals(tipo)) {
			Supplier destino = m.getPagoProveedor();
			if (destino != null) {
				destino.setBalance((destino.getBalance() != null ? destino.getBalance() : 0.0) - montoNuevo);
				supplierRepository.save(destino);
			}
			if (m.getCuentaOrigen() != null) {
				double comisionNueva = round2(montoNuevo * 0.004);
				AccountCop c = m.getCuentaOrigen();
				boolean esBanco = "BANCOLOMBIA".equalsIgnoreCase(String.valueOf(c.getBankType()));
				double deduccionHoy = esBanco ? montoNuevo : (montoNuevo + comisionNueva);
				m.setComision(comisionNueva);
				m.setComisionAplicada(!esBanco);
				c.setBalance(round2((c.getBalance() != null ? c.getBalance() : 0.0) - deduccionHoy));
				accountCopRepository.save(c);
			} else if (m.getCaja() != null) {
				m.setComision(0.0);
				Efectivo caja = m.getCaja();
				caja.setSaldo(round2((caja.getSaldo() != null ? caja.getSaldo() : 0.0) - montoNuevo));
				efectivoRepository.save(caja);
			} else if (m.getProveedorOrigen() != null) {
				Supplier po = m.getProveedorOrigen();
				po.setBalance((po.getBalance() != null ? po.getBalance() : 0.0) + montoNuevo);
				supplierRepository.save(po);
			} else if (m.getPagoCliente() != null) {
				Cliente cl = m.getPagoCliente();
				cl.setSaldo((cl.getSaldo() != null ? cl.getSaldo() : 0.0) + montoNuevo);
				clienteRepository.save(cl);
			}
		} else if (tipo.startsWith("RETIRO")) {
			AccountCop cuenta = m.getCuentaOrigen();
			Efectivo caja = m.getCaja();
			double comisionNueva = round2(montoNuevo * 0.004);
			boolean diferir4x1000 = cuenta != null && "BANCOLOMBIA".equalsIgnoreCase(String.valueOf(cuenta.getBankType()));
			m.setComision(comisionNueva);
			m.setComisionAplicada(!diferir4x1000);
			double deduccionHoy = diferir4x1000 ? montoNuevo : (montoNuevo + comisionNueva);
			if (cuenta != null) {
				cuenta.setBalance(round2((cuenta.getBalance() != null ? cuenta.getBalance() : 0.0) - deduccionHoy));
				asegurarCupoHoy(cuenta);
				java.time.LocalDate diaRetiro = m.getFecha() != null ? m.getFecha().toLocalDate() : null;
				java.time.LocalDate hoy = java.time.LocalDate.now(ZONE_BOGOTA);
				if (diaRetiro != null && diaRetiro.equals(hoy) && cuenta.getBankType() != null) {
					if (tipo.contains("CAJERO")) {
						double disp = cuenta.getCupoCajeroDisponibleHoy() != null ? cuenta.getCupoCajeroDisponibleHoy() : 0.0;
						cuenta.setCupoCajeroDisponibleHoy(round2(Math.max(0.0, disp - montoNuevo)));
					} else if (tipo.contains("CORRESPONSAL")) {
						double disp = cuenta.getCupoCorresponsalDisponibleHoy() != null ? cuenta.getCupoCorresponsalDisponibleHoy() : 0.0;
						cuenta.setCupoCorresponsalDisponibleHoy(round2(Math.max(0.0, disp - montoNuevo)));
					}
					double cj = cuenta.getCupoCajeroDisponibleHoy() != null ? cuenta.getCupoCajeroDisponibleHoy() : 0.0;
					double co = cuenta.getCupoCorresponsalDisponibleHoy() != null ? cuenta.getCupoCorresponsalDisponibleHoy() : 0.0;
					cuenta.setCupoDisponibleHoy(round2(cj + co));
				}
				accountCopRepository.save(cuenta);
			}
			if (caja != null) {
				caja.setSaldo(round2((caja.getSaldo() != null ? caja.getSaldo() : 0.0) + montoNuevo));
				efectivoRepository.save(caja);
			}
		} else if ("TRANSFERENCIA CAJA".equals(tipo)) {
			m.setComision(0.0);
			Efectivo origen = m.getCaja();
			Efectivo destino = m.getCajaDestino();
			if (origen != null) {
				origen.setSaldo(round2((origen.getSaldo() != null ? origen.getSaldo() : 0.0) - montoNuevo));
				efectivoRepository.save(origen);
			}
			if (destino != null) {
				destino.setSaldo(round2((destino.getSaldo() != null ? destino.getSaldo() : 0.0) + montoNuevo));
				efectivoRepository.save(destino);
			}
		}

		// ── 4) Histórico de caja: recalcular hacia adelante (NUNCA hacia atrás) ──
		boolean cajaOrigenCambio = cajaOrigenAntes != null && m.getCaja() != null
				&& !cajaOrigenAntes.getId().equals(m.getCaja().getId());
		if (cajaOrigenCambio) {
			// Caso raro: el movimiento se reasignó a OTRA caja. No tiene sentido un
			// delta entre dos cajas distintas, así que recalculamos ambas por completo.
			recalcularHistoricoCaja(cajaOrigenAntes.getId());
			recalcularHistoricoCaja(m.getCaja().getId());
		} else if (m.getCaja() != null && saldoCajaOrigenAntes != null) {
			double deltaCaja = round2(m.getCaja().getSaldo() - saldoCajaOrigenAntes);
			if (deltaCaja != 0.0) {
				Double resultanteViejo = m.getSaldoCajaResultante();
				double base = resultanteViejo != null ? resultanteViejo : saldoCajaOrigenAntes;
				m.setSaldoCajaResultante(round2(base + deltaCaja));
				propagarDeltaCaja(m.getCaja().getId(), m.getFecha(), deltaCaja);
			} else if (m.getSaldoCajaResultante() == null) {
				m.setSaldoCajaResultante(m.getCaja().getSaldo());
			}
		}
		if (m.getCajaDestino() != null && saldoCajaDestinoAntes != null) {
			double deltaCajaDestino = round2(m.getCajaDestino().getSaldo() - saldoCajaDestinoAntes);
			if (deltaCajaDestino != 0.0) {
				Double resultanteDestinoViejo = m.getSaldoCajaDestinoResultante();
				double baseDestino = resultanteDestinoViejo != null ? resultanteDestinoViejo : saldoCajaDestinoAntes;
				m.setSaldoCajaDestinoResultante(round2(baseDestino + deltaCajaDestino));
				propagarDeltaCaja(m.getCajaDestino().getId(), m.getFecha(), deltaCajaDestino);
			} else if (m.getSaldoCajaDestinoResultante() == null) {
				m.setSaldoCajaDestinoResultante(m.getCajaDestino().getSaldo());
			}
		}

		return movimientoRepository.save(m);
	}

	@Override
	public List<Movimiento> listarPagosProveedorPorId(Integer proveedorId) {
		return movimientoRepository.findByTipoAndPagoProveedor_Id("PAGO PROVEEDOR", proveedorId);
	}

	@Override
    public List<Movimiento> listarPagosCuentaPorId(Integer cuentaId) {
        // Ahora retorna TODOS los movimientos relacionados a la cuenta (origen o destino)
        if (cuentaId == null) throw new IllegalArgumentException("Cuenta id no puede ser nulo");
        // Valida existencia (opcional pero útil para errores 404 tempranos)
        accountCopRepository.findById(cuentaId)
            .orElseThrow(() -> new RuntimeException("Cuenta no encontrada: " + cuentaId));

        return movimientoRepository
            .findByCuentaOrigen_IdOrCuentaDestino_IdOrderByFechaDesc(cuentaId, cuentaId);
    }

	@Override
	public List<Movimiento> listarMovimientosPorCliente(Integer clienteId) {
		if (clienteId == null)
			throw new IllegalArgumentException("clienteId no puede ser nulo");
		return movimientoRepository.findByPagoCliente_IdOrClienteOrigen_IdOrderByFechaDesc(clienteId, clienteId);
	}

	@Override
	public Movimiento registrarPagoCaja(Integer clienteId, Integer cajaId, Double monto) {

		Cliente clienteOrigen = clienteRepository.findById(clienteId)
				.orElseThrow(() -> new RuntimeException("No se encontro el cliente"));
		Efectivo cajaDestino = efectivoRepository.findById(cajaId)
				.orElseThrow(() -> new RuntimeException("No se encontro la caja"));
		Movimiento pagoCaja = new Movimiento();

		clienteOrigen.setSaldo(clienteOrigen.getSaldo() + monto);
		cajaDestino.setSaldo(cajaDestino.getSaldo() + monto);

		efectivoRepository.save(cajaDestino);
		clienteRepository.save(clienteOrigen);

		// El cliente nos da efectivo → ENTRA a la caja (sin 4x1000, es efectivo).
		pagoCaja.setTipo("PAGO CLIENTE A CAJA");
		pagoCaja.setFecha(LocalDateTime.now());
		pagoCaja.setComision(0.0);
		pagoCaja.setCaja(cajaDestino);
		pagoCaja.setClienteOrigen(clienteOrigen);
		pagoCaja.setMonto(monto);
		pagoCaja.setSaldoCajaResultante(cajaDestino.getSaldo());
		return movimientoRepository.save(pagoCaja);
	}

	@Override
	@Transactional
	public Movimiento registrarPagoClienteACliente(PagoClienteAClienteDto dto) {
		if (dto.getClienteOrigenId() == null || dto.getClienteDestinoId() == null)
			throw new IllegalArgumentException("Debe indicar cliente origen y destino");
		if (Objects.equals(dto.getClienteOrigenId(), dto.getClienteDestinoId()))
			throw new IllegalArgumentException("Origen y destino no pueden ser el mismo cliente");
		if (dto.getUsdt() == null || dto.getUsdt() <= 0)
			throw new IllegalArgumentException("El monto USDT debe ser > 0");
		if (dto.getTasaOrigen() == null || dto.getTasaOrigen() <= 0 || dto.getTasaDestino() == null
				|| dto.getTasaDestino() <= 0)
			throw new IllegalArgumentException("Las tasas deben ser > 0");

		Cliente origen = clienteRepository.findById(dto.getClienteOrigenId())
				.orElseThrow(() -> new RuntimeException("Cliente origen no encontrado"));
		Cliente destino = clienteRepository.findById(dto.getClienteDestinoId())
				.orElseThrow(() -> new RuntimeException("Cliente destino no encontrado"));

		// Mapeo a COP según tus tasas
		double pesosOrigen = dto.getUsdt() * dto.getTasaOrigen();
		double pesosDestino = dto.getUsdt() * dto.getTasaDestino();

		// Saldos actuales (deuda positiva a favor del cliente)
		double so = origen.getSaldo() != null ? origen.getSaldo() : 0.0;
		double sd = destino.getSaldo() != null ? destino.getSaldo() : 0.0;

		// ❗ Según tu regla:
		// - Al ORIGEN se le SUMA (ustedes le deben más)
		// - Al DESTINO se le RESTA (les debe menos / ustedes le deben menos)
		so = round2(so + pesosOrigen);
		sd = round2(sd - pesosDestino);

		origen.setSaldo(so);
		destino.setSaldo(sd);

		clienteRepository.save(origen);
		clienteRepository.save(destino);

		Movimiento m = new Movimiento();
		m.setTipo("PAGO C2C"); // o "PAGO CLIENTE A CLIENTE"
		m.setFecha(LocalDateTime.now());

		// Campos C2C
		m.setUsdt(dto.getUsdt());
		m.setTasaOrigen(dto.getTasaOrigen());
		m.setTasaDestino(dto.getTasaDestino());
		m.setPesosOrigen(pesosOrigen);
		m.setPesosDestino(pesosDestino);

		// Participantes
		m.setClienteOrigen(origen); // el que "paga" (aumenta su saldo)
		m.setPagoCliente(destino); // usamos este como "cliente destino" (disminuye su saldo)

		// Si agregaste 'nota/descripcion' en el DTO y la entidad, guarda aquí
		// m.setDescripcion(dto.getNota());

		return movimientoRepository.save(m);
	}

	private static double round2(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	// ══════════════════════════════════════════════════════════════════
	// Histórico de caja: saldoCajaResultante por movimiento
	// ══════════════════════════════════════════════════════════════════

	/**
	 * Propaga un delta de saldo a todos los movimientos de una caja (como origen
	 * o como destino) que ocurrieron DESPUÉS de la fecha dada. Se usa al editar o
	 * eliminar un movimiento viejo: los movimientos ANTERIORES nunca se tocan
	 * (ya eran un hecho histórico antes de la corrección), pero todo lo que pasó
	 * después debe correrse por el mismo delta para que el histórico siga
	 * cuadrando con el saldo real actual de la caja.
	 */
	@Override
	@Transactional
	public void propagarDeltaCaja(Integer cajaId, LocalDateTime fechaDesde, double delta) {
		if (cajaId == null || fechaDesde == null || delta == 0.0) return;

		List<Movimiento> comoOrigen = movimientoRepository.findByCaja_IdAndFechaAfterOrderByFechaAsc(cajaId, fechaDesde);
		for (Movimiento post : comoOrigen) {
			if (post.getSaldoCajaResultante() != null) {
				post.setSaldoCajaResultante(round2(post.getSaldoCajaResultante() + delta));
				movimientoRepository.save(post);
			}
		}
		List<Movimiento> comoDestino = movimientoRepository.findByCajaDestino_IdAndFechaAfterOrderByFechaAsc(cajaId, fechaDesde);
		for (Movimiento post : comoDestino) {
			if (post.getSaldoCajaDestinoResultante() != null) {
				post.setSaldoCajaDestinoResultante(round2(post.getSaldoCajaDestinoResultante() + delta));
				movimientoRepository.save(post);
			}
		}
	}

	/**
	 * Cuánto sumó/restó ESTE movimiento a la caja indicada (positivo = entró,
	 * negativo = salió), usado solo para reconstruir el histórico desde cero
	 * (backfill/reparación) — no para la operación normal de crear/editar.
	 */
	private double deltaParaCaja(Movimiento m, Integer cajaId) {
		boolean esOrigen = m.getCaja() != null && cajaId.equals(m.getCaja().getId());
		double monto = m.getMonto() != null ? m.getMonto() : 0.0;

		if (!esOrigen) {
			// Hoy solo TRANSFERENCIA CAJA usa cajaDestino, y siempre ENTRA a la caja destino.
			return monto;
		}

		String tipo = m.getTipo() != null ? m.getTipo() : "";
		switch (tipo) {
			case "DEPOSITO":
			case "TRANSFERENCIA CAJA":
			case "PAGO PROVEEDOR":
			case "PAGO RETIRADOR":
			case "GASTO":
				return -monto;
			case "PRESTAMO PROVEEDOR A CAJA":
			case "PRESTAMO CLIENTE A CAJA":
			case "PAGO CLIENTE A CAJA":
			case "PAGO PROVEEDOR A CAJA": // tipo legado
				return monto;
			case "AJUSTE_SALDO_CAJA":
				return m.getDiferencia() != null ? m.getDiferencia() : 0.0;
			default:
				if (tipo.startsWith("RETIRO")) return monto;
				return 0.0;
		}
	}

	/**
	 * Reconstruye desde cero el saldoCajaResultante de TODOS los movimientos de
	 * una caja, ordenados del más viejo al más nuevo. El punto de partida se
	 * calcula ("ancla") de forma que, al reproducir todo el historial, el
	 * resultado del último movimiento cuadre EXACTO con el saldo real actual de
	 * la caja — así no importa si hubo algún ajuste fuera del historial de
	 * movimientos, el backfill igual queda consistente con la realidad de hoy.
	 */
	@Override
	@Transactional
	public void recalcularHistoricoCaja(Integer cajaId) {
		if (cajaId == null) return;
		Efectivo caja = efectivoRepository.findById(cajaId)
				.orElseThrow(() -> new RuntimeException("Caja no encontrada: " + cajaId));

		List<Movimiento> movimientos = movimientoRepository.findByCaja_IdOrCajaDestino_IdOrderByFechaAsc(cajaId, cajaId);

		double sumaDeltas = 0.0;
		for (Movimiento m : movimientos) {
			sumaDeltas += deltaParaCaja(m, cajaId);
		}

		double saldoActual = caja.getSaldo() != null ? caja.getSaldo() : 0.0;
		double ancla = round2(saldoActual - sumaDeltas);

		double acumulado = ancla;
		for (Movimiento m : movimientos) {
			acumulado = round2(acumulado + deltaParaCaja(m, cajaId));
			boolean esOrigen = m.getCaja() != null && cajaId.equals(m.getCaja().getId());
			if (esOrigen) {
				m.setSaldoCajaResultante(acumulado);
			} else {
				m.setSaldoCajaDestinoResultante(acumulado);
			}
			movimientoRepository.save(m);
		}
	}

	/** Backfill inicial de uso único: recalcula el histórico de TODAS las cajas existentes. */
	@Override
	@Transactional
	public void recalcularHistoricoCajaTodas() {
		List<Efectivo> todas = efectivoRepository.findAll();
		for (Efectivo caja : todas) {
			recalcularHistoricoCaja(caja.getId());
		}
	}

	@Override
	public List<Movimiento> listarMovimientosPorCaja(Integer cajaId) {
		if (cajaId == null)
			throw new IllegalArgumentException("cajaId no puede ser nulo");
		// Incluye tanto los movimientos donde la caja es origen como aquellos
		// donde es destino (para que los traspasos entre cajas aparezcan en ambas).
		return movimientoRepository.findByCaja_IdOrCajaDestino_IdOrderByFechaDesc(cajaId, cajaId);
	}

	@Override
	public List<MovimientoDTO> listarMovimientosCajaLite(Integer cajaId) {
		if (cajaId == null)
			throw new IllegalArgumentException("cajaId no puede ser nulo");
		return movimientoRepository.findMovimientosCajaLite(cajaId);
	}

	@Override
	public List<MovimientoDTO> listarMovimientosCajaLiteEntreFechas(Integer cajaId, LocalDateTime desde, LocalDateTime hasta) {
		if (cajaId == null)
			throw new IllegalArgumentException("cajaId no puede ser nulo");
		return movimientoRepository.findMovimientosCajaLiteEntreFechas(cajaId, desde, hasta);
	}

	@Override
	public Double obtenerSaldoInicialDelDia(Integer cajaId, LocalDateTime desde) {
		if (cajaId == null)
			throw new IllegalArgumentException("cajaId no puede ser nulo");
		List<Movimiento> anteriores = movimientoRepository.findUltimoMovimientoDeCajaAntesDe(
				cajaId, desde, org.springframework.data.domain.PageRequest.of(0, 1));
		if (anteriores.isEmpty())
			return null;
		Movimiento ultimo = anteriores.get(0);
		// El movimiento pudo tocar esta caja como origen o como destino (ej. TRANSFERENCIA
		// CAJA) — tomamos el campo que corresponda según en cuál rol aparece esta caja.
		if (ultimo.getCaja() != null && cajaId.equals(ultimo.getCaja().getId())) {
			return ultimo.getSaldoCajaResultante();
		}
		if (ultimo.getCajaDestino() != null && cajaId.equals(ultimo.getCajaDestino().getId())) {
			return ultimo.getSaldoCajaDestinoResultante();
		}
		return null;
	}

	@Override
	@Transactional
	public Movimiento registrarPagoClienteAProveedor(PagoClienteAProveedorDto dto) {
	    if (dto.getClienteOrigenId() == null || dto.getProveedorDestinoId() == null)
	        throw new IllegalArgumentException("Debe indicar cliente origen y proveedor destino");
	    if (dto.getUsdt() == null || dto.getUsdt() <= 0)
	        throw new IllegalArgumentException("El monto USDT debe ser > 0");
	    if (dto.getTasaCliente() == null || dto.getTasaCliente() <= 0 ||
	        dto.getTasaProveedor() == null || dto.getTasaProveedor() <= 0)
	        throw new IllegalArgumentException("Las tasas deben ser > 0");

	    Cliente cliente = clienteRepository.findById(dto.getClienteOrigenId())
	            .orElseThrow(() -> new RuntimeException("Cliente origen no encontrado"));
	    Supplier proveedor = supplierRepository.findById(dto.getProveedorDestinoId())
	            .orElseThrow(() -> new RuntimeException("Proveedor destino no encontrado"));

	    double pesosCliente   = dto.getUsdt() * dto.getTasaCliente();
	    double pesosProveedor = dto.getUsdt() * dto.getTasaProveedor();


	    double sc = cliente.getSaldo()   != null ? cliente.getSaldo()   : 0.0;
	    double sp = proveedor.getBalance()!= null ? proveedor.getBalance(): 0.0;

	    sc = round2(sc + pesosCliente);
	    sp = round2(sp - pesosProveedor);

	    cliente.setSaldo(sc);
	    proveedor.setBalance(sp);

	    clienteRepository.save(cliente);
	    supplierRepository.save(proveedor);

	    Movimiento m = new Movimiento();
	    m.setTipo("PAGO EN USDT CLIENTE PROVEEDOR");
	    m.setFecha(LocalDateTime.now());

	    m.setUsdt(dto.getUsdt());
	    m.setTasaOrigen(dto.getTasaCliente());
	    m.setTasaDestino(dto.getTasaProveedor());
	    m.setPesosOrigen(pesosCliente);
	    m.setPesosDestino(pesosProveedor);

	    m.setClienteOrigen(cliente);
	    m.setPagoProveedor(proveedor);

	    return movimientoRepository.save(m);
	}
	@Override
	@Transactional
	public Movimiento registrarPagoProveedorACliente(PagoProveedorAClienteDto dto) {
	    if (dto.getProveedorOrigenId() == null || dto.getClienteDestinoId() == null)
	        throw new IllegalArgumentException("Debe indicar proveedor origen y cliente destino");
	    if (dto.getUsdt() == null || dto.getUsdt() <= 0)
	        throw new IllegalArgumentException("El monto USDT debe ser > 0");
	    if (dto.getTasaProveedor() == null || dto.getTasaProveedor() <= 0 ||
	        dto.getTasaCliente() == null   || dto.getTasaCliente()   <= 0)
	        throw new IllegalArgumentException("Las tasas deben ser > 0");

	    Supplier proveedor = supplierRepository.findById(dto.getProveedorOrigenId())
	            .orElseThrow(() -> new RuntimeException("Proveedor origen no encontrado"));
	    Cliente  cliente   = clienteRepository.findById(dto.getClienteDestinoId())
	            .orElseThrow(() -> new RuntimeException("Cliente destino no encontrado"));

	    // COP equivalentes según cada tasa
	    double pesosProveedor = dto.getUsdt() * dto.getTasaProveedor(); // origen (proveedor)
	    double pesosCliente   = dto.getUsdt() * dto.getTasaCliente();   // destino (cliente)

	    double sp = proveedor.getBalance() != null ? proveedor.getBalance() : 0.0;
	    double sc = cliente.getSaldo()     != null ? cliente.getSaldo()   : 0.0;

	    // Regla inversa a Cliente→Proveedor:
	    // Proveedor paga -> nos debe más (sube su balance)
	    // Cliente recibe -> le debemos menos (baja su saldo)
	    sp = round2(sp + pesosProveedor);
	    sc = round2(sc - pesosCliente);

	    proveedor.setBalance(sp);
	    cliente.setSaldo(sc);

	    supplierRepository.save(proveedor);
	    clienteRepository.save(cliente);

	    Movimiento m = new Movimiento();
	    m.setTipo("PAGO EN USDT PROVEEDOR CLIENTE");
	    m.setFecha(LocalDateTime.now());

	    // Monto y tasas
	    m.setUsdt(dto.getUsdt());
	    m.setTasaOrigen(dto.getTasaProveedor()); // origen = proveedor
	    m.setTasaDestino(dto.getTasaCliente());  // destino = cliente
	    m.setPesosOrigen(pesosProveedor);
	    m.setPesosDestino(pesosCliente);

	    // Participantes
	    m.setProveedorOrigen(proveedor);
	    m.setPagoCliente(cliente);

	    // Si agregaste nota/descripción en la entidad:
	    // m.setDescripcion(dto.getNota());

	    return movimientoRepository.save(m);
	}

	
	@Override
	@Transactional
	public Movimiento registrarPagoClienteAClienteCop(Integer clienteOrigenId, Integer clienteDestinoId, Double montoCop) {
	    if (clienteOrigenId == null || clienteDestinoId == null)
	        throw new IllegalArgumentException("Debe indicar cliente origen y cliente destino");
	    if (Objects.equals(clienteOrigenId, clienteDestinoId))
	        throw new IllegalArgumentException("Origen y destino no pueden ser el mismo cliente");
	    if (montoCop == null || montoCop <= 0)
	        throw new IllegalArgumentException("El monto COP debe ser > 0");

	    Cliente origen  = clienteRepository.findById(clienteOrigenId)
	            .orElseThrow(() -> new RuntimeException("Cliente origen no encontrado"));
	    Cliente destino = clienteRepository.findById(clienteDestinoId)
	            .orElseThrow(() -> new RuntimeException("Cliente destino no encontrado"));

	    double so = origen.getSaldo()  != null ? origen.getSaldo()  : 0.0;
	    double sd = destino.getSaldo() != null ? destino.getSaldo() : 0.0;

	    // Regla: a ORIGEN se le suma (ustedes le deben más), a DESTINO se le resta
	    so = round2(so + montoCop);
	    sd = round2(sd - montoCop);

	    origen.setSaldo(so);
	    destino.setSaldo(sd);

	    clienteRepository.save(origen);
	    clienteRepository.save(destino);

	    Movimiento m = new Movimiento();
	    m.setTipo("PAGO C2C COP");        // ← nuevo tipo
	    m.setFecha(LocalDateTime.now());
	    m.setMonto(montoCop);
	    m.setClienteOrigen(origen);       // quién "paga" (aumenta su saldo)
	    m.setPagoCliente(destino);        // quién "recibe" (disminuye su saldo)
	    m.setComision(0.0);               // no hay comisión

	    // Campos USDT/tasas NO se tocan (quedan null)
	    return movimientoRepository.save(m);
	}
	
	// MovimientoServiceImplement.java
	@Override
	@Transactional
	public Movimiento registrarAjusteSaldo(AjusteSaldoDto dto) {
	    if (dto.getEntidad() == null || dto.getEntidadId() == null) {
	        throw new IllegalArgumentException("Entidad y entidadId son obligatorios");
	    }
	    if (dto.getMonto() == null || dto.getMonto() <= 0) {
	        throw new IllegalArgumentException("monto es obligatorio y debe ser > 0");
	    }
	    if (dto.getEntrada() == null) {
	        throw new IllegalArgumentException("Debe indicar si es entrada (true) o salida (false)");
	    }
	    if (dto.getMotivo() == null || dto.getMotivo().isBlank()) {
	        throw new IllegalArgumentException("motivo es obligatorio");
	    }

	    LocalDateTime ahora = LocalDateTime.now();
	    double monto = dto.getMonto();
	    // entrada = +monto, salida = -monto
	    double factor = Boolean.TRUE.equals(dto.getEntrada()) ? 1.0 : -1.0;
	    double dif = round2(monto * factor);   // diferencia firmada (+/-)

	    switch (dto.getEntidad().toUpperCase()) {

	        case "CLIENTE": {
	            Cliente cli = clienteRepository.findById(dto.getEntidadId())
	                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

	            double anterior = cli.getSaldo() != null ? cli.getSaldo() : 0.0;
	            double nuevo    = round2(anterior + dif);

	            // Actualiza materializado
	            cli.setSaldo(nuevo);
	            clienteRepository.save(cli);

	            Movimiento m = Movimiento.builder()
	                    .tipo("AJUSTE_SALDO_CLIENTE")
	                    .fecha(ahora)
	                    .monto(monto)                // monto absoluto del ajuste
	                    .motivo(dto.getMotivo())
	                    .actor(dto.getActor())
	                    .saldoAnterior(anterior)
	                    .saldoNuevo(nuevo)
	                    .diferencia(dif)             // firmado (+/-)
	                    .ajusteCliente(cli)
	                    .build();

	            return movimientoRepository.save(m);
	        }

	        case "PROVEEDOR": {
	            Supplier prov = supplierRepository.findById(dto.getEntidadId())
	                    .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));

	            double anterior = prov.getBalance() != null ? prov.getBalance() : 0.0;
	            double nuevo    = round2(anterior + dif);

	            prov.setBalance(nuevo);
	            supplierRepository.save(prov);

	            Movimiento m = Movimiento.builder()
	                    .tipo("AJUSTE_SALDO_PROVEEDOR")
	                    .fecha(ahora)
	                    .monto(monto)
	                    .motivo(dto.getMotivo())
	                    .actor(dto.getActor())
	                    .saldoAnterior(anterior)
	                    .saldoNuevo(nuevo)
	                    .diferencia(dif)
	                    .ajusteProveedor(prov)
	                    .build();

	            return movimientoRepository.save(m);
	        }

	        case "CUENTACOP": {
	            AccountCop acc = accountCopRepository.findById(dto.getEntidadId())
	                    .orElseThrow(() -> new RuntimeException("Cuenta COP no encontrada"));

	            double anterior = acc.getBalance() != null ? acc.getBalance() : 0.0;
	            double nuevo    = round2(anterior + dif);

	            acc.setBalance(nuevo);
	            accountCopRepository.save(acc);

	            Movimiento m = Movimiento.builder()
	                    .tipo("AJUSTE_SALDO_COP")
	                    .fecha(ahora)
	                    .monto(monto)
	                    .motivo(dto.getMotivo())
	                    .actor(dto.getActor())
	                    .saldoAnterior(anterior)
	                    .saldoNuevo(nuevo)
	                    .diferencia(dif)
	                    .ajusteCuentaCop(acc)
	                    .build();

	            return movimientoRepository.save(m);
	        }

	        case "CAJA": {
	            Efectivo caja = efectivoRepository.findById(dto.getEntidadId())
	                    .orElseThrow(() -> new RuntimeException("Caja no encontrada"));

	            double anterior = caja.getSaldo() != null ? caja.getSaldo() : 0.0;
	            double nuevo    = round2(anterior + dif);

	            caja.setSaldo(nuevo);
	            efectivoRepository.save(caja);

	            Movimiento m = Movimiento.builder()
	                    .tipo("AJUSTE_SALDO_CAJA")
	                    .fecha(ahora)
	                    .monto(monto)
	                    .motivo(dto.getMotivo())
	                    .actor(dto.getActor())
	                    .saldoAnterior(anterior)
	                    .saldoNuevo(nuevo)
	                    .diferencia(dif)
	                    .caja(caja)                 // usamos el campo caja del movimiento
	                    .saldoCajaResultante(nuevo)
	                    .build();

	            return movimientoRepository.save(m);
	        }

	        default:
	            throw new IllegalArgumentException("Entidad no soportada para ajuste: " + dto.getEntidad());
	    }
	}

	
	@Override
	@Transactional
	public Movimiento registrarPagoCuentaCopACliente(Integer cuentaCopId, Integer clienteId, Double monto) {

	    if (cuentaCopId == null || clienteId == null)
	        throw new IllegalArgumentException("Debe indicar cuenta COP y cliente destino");

	    if (monto == null || monto <= 0)
	        throw new IllegalArgumentException("El monto debe ser > 0");

	    AccountCop cuenta = accountCopRepository.findById(cuentaCopId)
	            .orElseThrow(() -> new RuntimeException("Cuenta COP no encontrada"));

	    Cliente cliente = clienteRepository.findById(clienteId)
	            .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

	    // 👉 comisión 4x1000 — diferida en BANCOLOMBIA (scheduler al día siguiente), inmediata en los demás.
	    double comision = monto * 0.004;
	    boolean esBanco = "BANCOLOMBIA".equalsIgnoreCase(String.valueOf(cuenta.getBankType()));
	    double deduccionHoy = esBanco ? monto : (monto + comision);

	    // 👉 ACTUALIZAR SALDOS
	    double saldoCuenta = cuenta.getBalance() != null ? cuenta.getBalance() : 0.0;
	    double saldoCliente = cliente.getSaldo() != null ? cliente.getSaldo() : 0.0;

	    cuenta.setBalance(saldoCuenta - deduccionHoy);  // la cuenta paga (4x1000 diferido si es Bancolombia)
	    cliente.setSaldo(saldoCliente + monto);       // el cliente recibe solo el monto

	    accountCopRepository.save(cuenta);
	    clienteRepository.save(cliente);

	    // 👉 REGISTRAR MOVIMIENTO
	    Movimiento mov = new Movimiento();
	    mov.setTipo("PAGO COP A CLIENTE");
	    mov.setFecha(LocalDateTime.now());
	    mov.setMonto(monto);
	    mov.setComision(comision);
	    mov.setComisionAplicada(!esBanco);

	    mov.setCuentaOrigen(cuenta);      // quien paga
	    mov.setPagoCliente(cliente);      // quien recibe

	    return movimientoRepository.save(mov);
	}
	
	@Override
	public List<Movimiento> listarAjustesCliente(Integer clienteId) {
	    if (clienteId == null) throw new IllegalArgumentException("clienteId no puede ser nulo");
	    return movimientoRepository.findByAjusteCliente_IdOrderByFechaDesc(clienteId);
	}

	@Override
	public List<Movimiento> listarAjustesProveedor(Integer proveedorId) {
	    if (proveedorId == null) throw new IllegalArgumentException("proveedorId no puede ser nulo");
	    return movimientoRepository.findByAjusteProveedor_IdOrderByFechaDesc(proveedorId);
	}

	@Override
	public List<Movimiento> listarAjustesCuentaCop(Integer cuentaId) {
	    if (cuentaId == null) throw new IllegalArgumentException("cuentaId no puede ser nulo");
	    return movimientoRepository.findByAjusteCuentaCop_IdOrderByFechaDesc(cuentaId);
	}

	@Override
	public List<Movimiento> listarAjustesCaja(Integer cajaId) {
	    if (cajaId == null) throw new IllegalArgumentException("cajaId no puede ser nulo");
	    return movimientoRepository.findByCaja_IdAndTipoOrderByFechaDesc(cajaId, "AJUSTE_SALDO_CAJA");
	}

	@Override
	public List<Movimiento> listarAjustesCajaEntreFechas(Integer cajaId, LocalDateTime desde, LocalDateTime hasta) {
	    if (cajaId == null) throw new IllegalArgumentException("cajaId no puede ser nulo");
	    return movimientoRepository.findByCaja_IdAndTipoAndFechaBetweenOrderByFechaDesc(cajaId, "AJUSTE_SALDO_CAJA", desde, hasta);
	}

	@Override
	@Transactional
	public void eliminarMovimiento(Integer id) {
	    Movimiento m = movimientoRepository.findById(id)
	            .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado"));

	    String tipo = m.getTipo() != null ? m.getTipo() : "";
	    double monto = m.getMonto() != null ? m.getMonto() : 0.0;

	    // ── COMPAT: registros VIEJOS "PAGO ... A CAJA" (se aplicaban como pago → BAJABAN la deuda).
	    //    Su reversa vuelve a SUBIR la deuda. Se mantiene para poder borrar registros antiguos sin descuadre.
	    if ("PAGO PROVEEDOR A CAJA".equals(tipo)) {
	        if (m.getCaja() != null) {
	            Efectivo caja = m.getCaja();
	            caja.setSaldo(round2((caja.getSaldo() != null ? caja.getSaldo() : 0.0) - monto));
	            efectivoRepository.save(caja);
	            propagarDeltaCaja(caja.getId(), m.getFecha(), -monto);
	        }
	        if (m.getProveedorOrigen() != null) {
	            Supplier po = m.getProveedorOrigen();
	            po.setBalance((po.getBalance() != null ? po.getBalance() : 0.0) + monto);
	            supplierRepository.save(po);
	        }
	        movimientoRepository.delete(m);
	        return;
	    }

	    if ("PAGO CLIENTE A CAJA".equals(tipo)) {
	        if (m.getCaja() != null) {
	            Efectivo caja = m.getCaja();
	            caja.setSaldo(round2((caja.getSaldo() != null ? caja.getSaldo() : 0.0) - monto));
	            efectivoRepository.save(caja);
	            propagarDeltaCaja(caja.getId(), m.getFecha(), -monto);
	        }
	        if (m.getClienteOrigen() != null) {
	            Cliente cl = m.getClienteOrigen();
	            cl.setSaldo((cl.getSaldo() != null ? cl.getSaldo() : 0.0) + monto);
	            clienteRepository.save(cl);
	        }
	        movimientoRepository.delete(m);
	        return;
	    }

	    // ── PRÉSTAMO ... A CAJA (nuevo): se aplicó como préstamo → SUBIÓ la deuda (+= monto).
	    //    Reversa: sale de la caja (entró) y la deuda vuelve a BAJAR (-= monto).
	    if ("PRESTAMO PROVEEDOR A CAJA".equals(tipo)) {
	        if (m.getCaja() != null) {
	            Efectivo caja = m.getCaja();
	            caja.setSaldo(round2((caja.getSaldo() != null ? caja.getSaldo() : 0.0) - monto));
	            efectivoRepository.save(caja);
	            propagarDeltaCaja(caja.getId(), m.getFecha(), -monto);
	        }
	        if (m.getProveedorOrigen() != null) {
	            Supplier po = m.getProveedorOrigen();
	            po.setBalance((po.getBalance() != null ? po.getBalance() : 0.0) - monto);
	            supplierRepository.save(po);
	        }
	        movimientoRepository.delete(m);
	        return;
	    }

	    if ("PRESTAMO CLIENTE A CAJA".equals(tipo)) {
	        if (m.getCaja() != null) {
	            Efectivo caja = m.getCaja();
	            caja.setSaldo(round2((caja.getSaldo() != null ? caja.getSaldo() : 0.0) - monto));
	            efectivoRepository.save(caja);
	            propagarDeltaCaja(caja.getId(), m.getFecha(), -monto);
	        }
	        if (m.getClienteOrigen() != null) {
	            Cliente cl = m.getClienteOrigen();
	            cl.setSaldo((cl.getSaldo() != null ? cl.getSaldo() : 0.0) - monto);
	            clienteRepository.save(cl);
	        }
	        movimientoRepository.delete(m);
	        return;
	    }

	    if ("PAGO PROVEEDOR".equals(tipo)) {
	        // Revertir EXACTO lo que hizo registrarPagoProveedor:
	        // 1) el proveedor destino recupera lo que se le restó.
	        Supplier destino = m.getPagoProveedor();
	        if (destino != null) {
	            destino.setBalance((destino.getBalance() != null ? destino.getBalance() : 0.0) + monto);
	            supplierRepository.save(destino);
	        }
	        // 2) revertir el origen (uno solo de estos se usó al crear).
	        if (m.getCuentaOrigen() != null) {
	            double comision = m.getComision() != null ? m.getComision() : 0.0;
	            // Solo devolver el 4x1000 si REALMENTE se descontó (Bancolombia pendiente = aún no).
	            boolean comisionYaAplicada = !Boolean.FALSE.equals(m.getComisionAplicada());
	            double aReversar = monto + (comisionYaAplicada ? comision : 0.0);
	            AccountCop c = m.getCuentaOrigen();
	            c.setBalance((c.getBalance() != null ? c.getBalance() : 0.0) + aReversar);
	            accountCopRepository.save(c);
	        } else if (m.getCaja() != null) {
	            Efectivo caja = m.getCaja();
	            caja.setSaldo((caja.getSaldo() != null ? caja.getSaldo() : 0.0) + monto);
	            efectivoRepository.save(caja);
	            propagarDeltaCaja(caja.getId(), m.getFecha(), monto);
	        } else if (m.getProveedorOrigen() != null) {
	            Supplier po = m.getProveedorOrigen();
	            po.setBalance((po.getBalance() != null ? po.getBalance() : 0.0) - monto);
	            supplierRepository.save(po);
	        } else if (m.getPagoCliente() != null) {
	            Cliente cl = m.getPagoCliente();
	            cl.setSaldo((cl.getSaldo() != null ? cl.getSaldo() : 0.0) - monto);
	            clienteRepository.save(cl);
	        }

	        movimientoRepository.delete(m);
	        return;
	    }

	    if ("TRANSFERENCIA CAJA".equals(tipo)) {
	        // Revertir el traspaso entre cajas: devolver al origen, quitar al destino.
	        Efectivo origen = m.getCaja();
	        Efectivo destino = m.getCajaDestino();
	        if (origen != null) {
	            origen.setSaldo(round2((origen.getSaldo() != null ? origen.getSaldo() : 0.0) + monto));
	            efectivoRepository.save(origen);
	            propagarDeltaCaja(origen.getId(), m.getFecha(), monto);
	        }
	        if (destino != null) {
	            destino.setSaldo(round2((destino.getSaldo() != null ? destino.getSaldo() : 0.0) - monto));
	            efectivoRepository.save(destino);
	            propagarDeltaCaja(destino.getId(), m.getFecha(), -monto);
	        }
	        movimientoRepository.delete(m);
	        return;
	    }

	    if (tipo.startsWith("RETIRO")) {
	        // Revertir EXACTO lo que hizo RegistrarRetiro:
	        AccountCop cuenta = m.getCuentaOrigen();
	        Efectivo caja = m.getCaja();
	        double comision = m.getComision() != null ? m.getComision() : 0.0;
	        // Solo devolver el 4x1000 si REALMENTE se descontó (Bancolombia pendiente = aún no).
	        boolean comisionYaAplicada = !Boolean.FALSE.equals(m.getComisionAplicada());
	        double aReversar = monto + (comisionYaAplicada ? comision : 0.0);

	        // 1) Devolver a la cuenta COP lo que salió (monto + 4x1000 si ya se había aplicado).
	        if (cuenta != null) {
	            cuenta.setBalance(round2((cuenta.getBalance() != null ? cuenta.getBalance() : 0.0) + aReversar));

	            // 2) Restablecer el cupo del tipo, SOLO si el retiro fue del día de hoy
	            //    (si fue otro día, el cupo ya se reseteó y no se debe tocar).
	            asegurarCupoHoy(cuenta);
	            java.time.LocalDate diaRetiro = m.getFecha() != null ? m.getFecha().toLocalDate() : null;
	            java.time.LocalDate hoy = java.time.LocalDate.now(ZONE_BOGOTA);
	            if (diaRetiro != null && diaRetiro.equals(hoy) && cuenta.getBankType() != null) {
	                if (tipo.contains("CAJERO")) {
	                    double max = CupoDiarioRules.maxCajeroPorBanco(cuenta.getBankType());
	                    double disp = cuenta.getCupoCajeroDisponibleHoy() != null ? cuenta.getCupoCajeroDisponibleHoy() : 0.0;
	                    cuenta.setCupoCajeroDisponibleHoy(round2(Math.min(max, disp + monto)));
	                } else if (tipo.contains("CORRESPONSAL")) {
	                    double max = CupoDiarioRules.maxCorresponsalPorBanco(cuenta.getBankType());
	                    double disp = cuenta.getCupoCorresponsalDisponibleHoy() != null ? cuenta.getCupoCorresponsalDisponibleHoy() : 0.0;
	                    cuenta.setCupoCorresponsalDisponibleHoy(round2(Math.min(max, disp + monto)));
	                }
	                // sincronizar el campo legacy (suma de ambos)
	                double cj = cuenta.getCupoCajeroDisponibleHoy() != null ? cuenta.getCupoCajeroDisponibleHoy() : 0.0;
	                double co = cuenta.getCupoCorresponsalDisponibleHoy() != null ? cuenta.getCupoCorresponsalDisponibleHoy() : 0.0;
	                cuenta.setCupoDisponibleHoy(round2(cj + co));
	            }
	            accountCopRepository.save(cuenta);
	        }

	        // 3) Quitar de la caja el monto que había entrado.
	        if (caja != null) {
	            caja.setSaldo(round2((caja.getSaldo() != null ? caja.getSaldo() : 0.0) - monto));
	            efectivoRepository.save(caja);
	            propagarDeltaCaja(caja.getId(), m.getFecha(), -monto);
	        }

	        movimientoRepository.delete(m);
	        return;
	    }

	    // Otros tipos (transferencias, ajustes, C2C, etc.) todavía no tienen reversa
	    // implementada: se rechaza para NO dejar saldos descuadrados.
	    throw new IllegalStateException(
	            "Este tipo de movimiento aún no se puede eliminar (solo PAGO PROVEEDOR, RETIRO y TRANSFERENCIA CAJA).");
	}

}
