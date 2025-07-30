package com.binance.web.movimientos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.Efectivo;
import com.binance.web.Entity.Movimiento;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.movimientos.MovimientoDTO;
import com.binance.web.Entity.Cliente;

@Service
public class MovimientoServiceImplement implements MovimientoService{
	
	@Autowired
	private MovimientoRepository movimientoRepository;
	@Autowired
	private AccountCopRepository accountCopRepository;
	@Autowired
	private EfectivoRepository efectivoRepository;
	@Autowired
	private ClienteRepository clienteRepository;

	@Override
	public Movimiento RegistrarTransferencia(Integer idCuentoFrom, Integer idCuentaTo, Double monto) {
		Optional<AccountCop> cuentaOrigen = accountCopRepository.findById(idCuentoFrom);
		Optional<AccountCop> cuentaDestino = accountCopRepository.findById(idCuentaTo);
		AccountCop cuentaFrom = cuentaOrigen.orElseThrow(() -> new RuntimeException("Cuenta de Origen no encontrada"));
		AccountCop cuentaTo = cuentaDestino.orElseThrow(()-> new RuntimeException("Cuenta Destino no encontrada"));
		
		Double montoConComision = monto * 1.004;
		Double comision = monto * 0.004;
		
		Movimiento nuevoMoviento = new Movimiento(null, "TRANSFERENCIA", LocalDateTime.now(), monto, cuentaTo, cuentaFrom, null, comision, null);
		
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
		Movimiento retiro = new Movimiento(null, "RETIRO", LocalDateTime.now(), monto, cuentaOrigen,null,caja, montoConComision, null);
		
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
		
		Movimiento deposito = new Movimiento(null, "DEPOSITO", LocalDateTime.now(), monto, null ,cuentaDestino,caja, 0.0, null);
		
		accountCopRepository.save(cuentaDestino);
		efectivoRepository.save(caja);
		
		return movimientoRepository.save(deposito);
	}
	
	public Movimiento registrarPagoCliente(Integer cuentaId, Integer clienteId, Double monto) {
		
		AccountCop cuentaDestino = accountCopRepository.findById(cuentaId).get();
		Cliente cliente = clienteRepository.findById(clienteId).get();
		
		cliente.setSaldo(cliente.getSaldo() + monto);
		cuentaDestino.setBalance(cuentaDestino.getBalance() + monto);
		
		Movimiento pago = new Movimiento(null, "PAGO", LocalDateTime.now(), monto, null, cuentaDestino, null, 0.0, cliente);
		
		accountCopRepository.save(cuentaDestino);
		clienteRepository.save(cliente);
		return movimientoRepository.save(pago);
		
	}

	@Override
	public List<Movimiento> listar() {
		// TODO Auto-generated method stub
		return movimientoRepository.findAll();
	}
	
	@Override
	public List<Movimiento> listarRetiros(){
		return movimientoRepository.findByTipo("RETIRO");
	}
	
	@Override
	public List<Movimiento> listarDepositos(){
		return movimientoRepository.findByTipo("DEPOSITO");
	}
	
	@Override
	public List<Movimiento> listarTransferencias(){
		return movimientoRepository.findByTipo("TRANSFERENCIA");
	}
	@Override
	public List<Movimiento> listarPagos(){
		return movimientoRepository.findByTipo("PAGO");
	}
}
