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
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.Repository.MovimientoRepository;
@Service
public class MovimientoServiceImplement implements MovimientoService{
	
	@Autowired
	private MovimientoRepository movimientoRepository;
	@Autowired
	private AccountCopRepository accountCopRepository;
	@Autowired
	private EfectivoRepository efectivoRepository;

	@Override
	public Movimiento RegistrarTransferencia(Integer idCuentoFrom, Integer idCuentaTo, Double monto) {
		Optional<AccountCop> cuentaOrigen = accountCopRepository.findById(idCuentoFrom);
		Optional<AccountCop> cuentaDestino = accountCopRepository.findById(idCuentaTo);
		AccountCop cuentaFrom = cuentaOrigen.orElseThrow(() -> new RuntimeException("Cuenta de Origen no encontrada"));
		AccountCop cuentaTo = cuentaDestino.orElseThrow(()-> new RuntimeException("Cuenta Destino no encontrada"));
		
		
		Movimiento nuevoMoviento = new Movimiento(null, "TRANSFERENCIA", LocalDateTime.now(), monto, cuentaTo, cuentaFrom, null);
		
		cuentaFrom.setBalance(cuentaFrom.getBalance() - monto);
		cuentaTo.setBalance(cuentaTo.getBalance() + monto);
		
		
		accountCopRepository.save(cuentaFrom);
		accountCopRepository.save(cuentaTo);
		
		return movimientoRepository.save(nuevoMoviento);
	}

	@Override
	public Movimiento RegistrarRetiro(Integer cuentaId, Double monto) {
		// TODO Auto-generated method stub
		AccountCop cuentaOrigen = accountCopRepository.findById(cuentaId).get();
		Efectivo caja = efectivoRepository.findByName("CAJA PRINCIPAL");
		
		Movimiento retiro = new Movimiento(null, "RETIRO", LocalDateTime.now(), monto, cuentaOrigen,null,caja);
		
		cuentaOrigen.setBalance(cuentaOrigen.getBalance() - monto);
		caja.setSaldo(caja.getSaldo() + monto);
		
		accountCopRepository.save(cuentaOrigen);
		efectivoRepository.save(caja);
		
		return movimientoRepository.save(retiro);
	}

	@Override
	public Movimiento RegistrarDeposito(Integer cuentaId, Double monto) {
		// TODO Auto-generated method stub
		AccountCop cuentaDestino = accountCopRepository.findById(cuentaId).get();
		Efectivo caja = efectivoRepository.findByName("CAJA PRINCIPAL");
		
		cuentaDestino.setBalance(cuentaDestino.getBalance() + monto);
		caja.setSaldo(caja.getSaldo() - monto);
		
		Movimiento retiro = new Movimiento(null, "DEPOSITO", LocalDateTime.now(), monto, null ,cuentaDestino,caja);
		
		accountCopRepository.save(cuentaDestino);
		efectivoRepository.save(caja);
		
		return movimientoRepository.save(retiro);
	}

	@Override
	public List<Movimiento> listar() {
		// TODO Auto-generated method stub
		return movimientoRepository.findAll();
	}
	
	

}
