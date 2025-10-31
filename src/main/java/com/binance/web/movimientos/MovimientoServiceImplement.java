package com.binance.web.movimientos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.Movimiento;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.model.PagoClienteAClienteDto;
import com.binance.web.model.PagoClienteAProveedorDto;
import com.binance.web.movimientos.MovimientoDTO;

import jakarta.transaction.Transactional;

import com.binance.web.Entity.Cliente;

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

	@Override
	public Movimiento RegistrarTransferencia(Integer idCuentoFrom, Integer idCuentaTo, Double monto) {
		AccountCop cuentaFrom = accountCopRepository.findById(idCuentoFrom)
				.orElseThrow(() -> new RuntimeException("Cuenta de Origen no encontrada"));
		AccountCop cuentaTo = accountCopRepository.findById(idCuentaTo)
				.orElseThrow(() -> new RuntimeException("Cuenta Destino no encontrada"));

		double comision = monto * 0.004;
		double montoConComision = monto + comision;

		Movimiento mov = Movimiento.builder().tipo("TRANSFERENCIA").fecha(LocalDateTime.now()).monto(monto)
				.cuentaOrigen(cuentaFrom) // ✅ origen correcto
				.cuentaDestino(cuentaTo) // ✅ destino correcto
				.comision(comision) // ✅ sólo la comisión, no “monto+comisión”
				.build();

		cuentaFrom.setBalance(cuentaFrom.getBalance() - montoConComision);
		cuentaTo.setBalance(cuentaTo.getBalance() + monto);
		accountCopRepository.save(cuentaFrom);
		accountCopRepository.save(cuentaTo);

		return movimientoRepository.save(mov);
	}

	@Override
	public Movimiento RegistrarRetiro(Integer cuentaId, Integer cajaId, Double monto) {
		AccountCop cuentaOrigen = accountCopRepository.findById(cuentaId).orElseThrow();
		Efectivo caja = efectivoRepository.findById(cajaId).orElseThrow();

		double comision = monto * 0.004;
		double montoConComision = monto + comision;

		Movimiento mov = Movimiento.builder().tipo("RETIRO").fecha(LocalDateTime.now()).monto(monto)
				.cuentaOrigen(cuentaOrigen).caja(caja).comision(comision) // ✅ era un bug: antes guardabas
																			// montoConComision
				.build();

		cuentaOrigen.setBalance(cuentaOrigen.getBalance() - montoConComision);
		caja.setSaldo(caja.getSaldo() + monto);
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

		if (cuentaCopId != null) {
			AccountCop cuentaCop = accountCopRepository.findById(cuentaCopId)
					.orElseThrow(() -> new RuntimeException("Cuenta de Origen no encontrada."));

			comision = monto * 0.004;
			Double montoConComision = monto + comision;

			// Actualizar el balance de la cuenta
			cuentaCop.setBalance(cuentaCop.getBalance() - montoConComision);
			accountCopRepository.save(cuentaCop);

			// Crear el objeto Movimiento
			pagoProveedor.setCuentaOrigen(cuentaCop);
			pagoProveedor.setComision(comision);

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

	@Override
	public List<Movimiento> listar() {
		// TODO Auto-generated method stub
		return movimientoRepository.findAll();
	}

	@Override
	public List<Movimiento> listarRetiros() {
		return movimientoRepository.findByTipo("RETIRO");
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
	public Movimiento actualizarMovimiento(Integer id, Double monto, Integer cuentaOrigenId, Integer cuentaDestinoId,
			Integer cajaId) {
		Movimiento movimiento = movimientoRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Movimiento no encontrado con id: " + id));

		if (monto != null) {
			movimiento.setMonto(monto);
		}

		if (cuentaOrigenId != null) {
			AccountCop cuentaOrigen = accountCopRepository.findById(cuentaOrigenId)
					.orElseThrow(() -> new RuntimeException("Cuenta origen no encontrada con id: " + cuentaOrigenId));
			movimiento.setCuentaOrigen(cuentaOrigen);
		}

		if (cuentaDestinoId != null) {
			AccountCop cuentaDestino = accountCopRepository.findById(cuentaDestinoId)
					.orElseThrow(() -> new RuntimeException("Cuenta destino no encontrada con id: " + cuentaDestinoId));
			movimiento.setCuentaDestino(cuentaDestino);
		}

		if (cajaId != null) {
			Efectivo caja = efectivoRepository.findById(cajaId)
					.orElseThrow(() -> new RuntimeException("Caja no encontrada con id: " + cajaId));
			movimiento.setCaja(caja);
		}

		return movimientoRepository.save(movimiento);
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

		pagoCaja.setCaja(cajaDestino);
		pagoCaja.setClienteOrigen(clienteOrigen);
		pagoCaja.setMonto(monto);
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
		m.setClienteOrigen(origen); // el que “paga” (aumenta su saldo)
		m.setPagoCliente(destino); // usamos este como “cliente destino” (disminuye su saldo)

		// Si agregaste 'nota/descripcion' en el DTO y la entidad, guarda aquí
		// m.setDescripcion(dto.getNota());

		return movimientoRepository.save(m);
	}

	private static double round2(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	@Override
	public List<Movimiento> listarMovimientosPorCaja(Integer cajaId) {
		if (cajaId == null)
			throw new IllegalArgumentException("cajaId no puede ser nulo");
		return movimientoRepository.findByCaja_IdOrderByFechaDesc(cajaId);
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
	    m.setClienteOrigen(origen);       // quién “paga” (aumenta su saldo)
	    m.setPagoCliente(destino);        // quién “recibe” (disminuye su saldo)
	    m.setComision(0.0);               // no hay comisión

	    // Campos USDT/tasas NO se tocan (quedan null)
	    return movimientoRepository.save(m);
	}


}
