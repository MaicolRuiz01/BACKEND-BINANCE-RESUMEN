package com.binance.web.movimientos;

import java.time.LocalDateTime;
import java.util.List;
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
		Optional<AccountCop> cuentaOrigen = accountCopRepository.findById(idCuentoFrom);
		Optional<AccountCop> cuentaDestino = accountCopRepository.findById(idCuentaTo);
		AccountCop cuentaFrom = cuentaOrigen.orElseThrow(() -> new RuntimeException("Cuenta de Origen no encontrada"));
		AccountCop cuentaTo = cuentaDestino.orElseThrow(() -> new RuntimeException("Cuenta Destino no encontrada"));

		Double montoConComision = monto * 1.004;
		Double comision = monto * 0.004;

		Movimiento nuevoMoviento = new Movimiento(null, "TRANSFERENCIA", LocalDateTime.now(), monto, cuentaFrom, cuentaTo, null,
				comision, null, null, null);

		cuentaFrom.setBalance(cuentaFrom.getBalance() - montoConComision);
		cuentaTo.setBalance(cuentaTo.getBalance() + monto);

		accountCopRepository.save(cuentaFrom);
		accountCopRepository.save(cuentaTo);

		return movimientoRepository.save(nuevoMoviento);
	}

	@Override
	public Movimiento RegistrarRetiro(Integer cuentaId, Integer cajaId, Double monto) {
		// TODO Auto-generated method stub
		AccountCop cuentaOrigen = accountCopRepository.findById(cuentaId).get();
		Efectivo caja = efectivoRepository.findById(cajaId).get();
		Double comision = monto * 0.004;
		Double montoConComision = monto * 1.004;
		Movimiento retiro = new Movimiento(null, "RETIRO", LocalDateTime.now(), monto, cuentaOrigen, null, caja,
				montoConComision, null, null, null);

		cuentaOrigen.setBalance(cuentaOrigen.getBalance() - montoConComision);
		caja.setSaldo(caja.getSaldo() + monto);

		accountCopRepository.save(cuentaOrigen);
		efectivoRepository.save(caja);

		return movimientoRepository.save(retiro);
	}

	@Override
	public Movimiento RegistrarDeposito(Integer cuentaId, Integer cajaId, Double monto) {
		// TODO Auto-generated method stub
		AccountCop cuentaDestino = accountCopRepository.findById(cuentaId).get();
		Efectivo caja = efectivoRepository.findById(cajaId).get();

		cuentaDestino.setBalance(cuentaDestino.getBalance() + monto);
		caja.setSaldo(caja.getSaldo() - monto);

		Movimiento deposito = new Movimiento(null, "DEPOSITO", LocalDateTime.now(), monto, null, cuentaDestino, caja,
				0.0, null, null, null);

		accountCopRepository.save(cuentaDestino);
		efectivoRepository.save(caja);

		return movimientoRepository.save(deposito);
	}

	public Movimiento registrarPagoCliente(Integer cuentaId, Integer clienteId, Double monto) {

		AccountCop cuentaDestino = accountCopRepository.findById(cuentaId).get();
		Cliente cliente = clienteRepository.findById(clienteId).get();

		cliente.setSaldo(cliente.getSaldo() + monto);
		cuentaDestino.setBalance(cuentaDestino.getBalance() + monto);

		Movimiento pago = new Movimiento(null, "PAGO", LocalDateTime.now(), monto, null, cuentaDestino, null, 0.0,
				cliente, null, null);

		accountCopRepository.save(cuentaDestino);
		clienteRepository.save(cliente);
		return movimientoRepository.save(pago);

	}

	@Override
	@Transactional
	public Movimiento registrarPagoProveedor(Integer cuentaCopId, Integer cajaId, Integer proveedorOrigenId,Integer proveedorDestinoId, Double monto) {

		if (cuentaCopId == null && cajaId == null && proveedorOrigenId == null) {
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
		else if (cajaId != null){
			Efectivo caja = efectivoRepository.findById(cajaId)
					.orElseThrow(() -> new RuntimeException("Caja no encontrada."));

			// Actualizar el saldo de la caja
			caja.setSaldo(caja.getSaldo() - monto);
			efectivoRepository.save(caja);

			// Crear el objeto Movimiento
			pagoProveedor.setCaja(caja);
			pagoProveedor.setComision(0.0); // No hay comisión por pagos en efectivo
		}
		else if(proveedorOrigenId != null) {
			Supplier proveedorOrigen = supplierRepository.findById(proveedorOrigenId).orElseThrow(()->new RuntimeException("Proveedor Origen no encontrado"));
			proveedorOrigen.setBalance(proveedorOrigen.getBalance() + monto);
			supplierRepository.save(proveedorOrigen);
			//pagoProveedor.setProveedorOrigen(proveedorOrigen);
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
	public List<Movimiento> listarMovimientosClienteId(Integer clienteId) {
		return movimientoRepository.findByTipoAndPagoCliente_Id("PAGO PROVEEDOR", clienteId);
	}

	public List<Movimiento> listarMovimientosPorCuentaId(Integer cuentaId) {
    return movimientoRepository.findByCuentaOrigenIdOrCuentaDestinoId(cuentaId, cuentaId);
}
}
