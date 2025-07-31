package com.binance.web.clientes;

import java.util.List;

import com.binance.web.Entity.Cliente;

public interface ClienteService {
	
	List<Cliente> allClientes();
	Cliente crearCliente(Cliente cliente);


}
