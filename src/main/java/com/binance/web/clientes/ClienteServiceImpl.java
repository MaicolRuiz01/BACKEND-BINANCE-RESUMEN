package com.binance.web.clientes;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.Cliente;
import com.binance.web.Repository.ClienteRepository;

@Service
public class ClienteServiceImpl implements ClienteService {
	
	@Autowired
	private ClienteRepository clienteRepository;

	@Override
	public List<Cliente> allClientes() {
		return clienteRepository.findAll();
	}
	
	@Override
    public Cliente crearCliente(Cliente cliente) {
        cliente.setId(null); // asegúrate de que sea nuevo
        if (cliente.getSaldo() == null) {
            cliente.setSaldo(0.0);
        }
        return clienteRepository.save(cliente);
    }

	// (Si quieres conservar este helper, quítale cualquier anotación web)
	public Cliente obtenerCliente(Integer clienteId) {
		return clienteRepository.findById(clienteId).orElse(null);
	}

	private static double round2(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	@Override
	@Transactional
	public void transferir(Integer origenId, Integer destinoId, Double monto, String nota) {
		// Validaciones mínimas
		if (origenId == null || destinoId == null) {
			throw new IllegalArgumentException("Debe seleccionar ambos clientes");
		}
		if (origenId.equals(destinoId)) {
			throw new IllegalArgumentException("El cliente origen y destino no pueden ser el mismo");
		}
		if (monto == null || monto <= 0) {
			throw new IllegalArgumentException("El monto debe ser mayor a 0");
		}

		// Cargar clientes
		Cliente origen = clienteRepository.findById(origenId)
				.orElseThrow(() -> new IllegalArgumentException("Cliente origen no existe"));
		Cliente destino = clienteRepository.findById(destinoId)
				.orElseThrow(() -> new IllegalArgumentException("Cliente destino no existe"));

		// Saldo suficiente (con pequeño epsilon por double)
		if ((origen.getSaldo() + 1e-6) < monto) {
			throw new IllegalStateException("Saldo insuficiente");
		}

		// Restar / sumar con redondeo a 2 decimales
		origen.setSaldo(round2(origen.getSaldo() - monto));
		destino.setSaldo(round2(destino.getSaldo() + monto));

		// Guardar ambos dentro de la misma transacción
		clienteRepository.save(origen);
		clienteRepository.save(destino);

		// (nota) Si luego necesitas auditar, aquí podrías registrar el movimiento.
	}
	 @Override
	    @Transactional
	    public Cliente reemplazar(Integer id, Cliente nuevo) {
	        Cliente existente = clienteRepository.findById(id)
	                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));

	        // Reemplazo total (no conservar valores viejos)
	        existente.setNombre(nuevo.getNombre());
	        existente.setNameUser(nuevo.getNameUser());
	        existente.setCorreo(nuevo.getCorreo());
	        existente.setSaldo(nuevo.getSaldo() != null ? nuevo.getSaldo() : 0.0);
	        existente.setAccountId(nuevo.getAccountId());
	        existente.setWallet(nuevo.getWallet());
	        existente.setBinanceId(nuevo.getBinanceId());

	        return clienteRepository.save(existente);
	    }
	 
	 
}
