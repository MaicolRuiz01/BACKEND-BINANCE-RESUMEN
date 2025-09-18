package com.binance.web.clientes;

import java.util.List;

import com.binance.web.Entity.Cliente;

public interface ClienteService {
	
	List<Cliente> allClientes();
	Cliente crearCliente(Cliente cliente);

	// === MÉTODO NUEVO: transferencia de saldo ===
	void transferir(Integer origenId, Integer destinoId, Double monto, String nota);
}
