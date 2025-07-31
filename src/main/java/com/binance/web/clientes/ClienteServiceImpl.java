package com.binance.web.clientes;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

import com.binance.web.Entity.Cliente;
import com.binance.web.Repository.ClienteRepository;

@Service
public class ClienteServiceImpl implements ClienteService{
	
	@Autowired
	private ClienteRepository clienteRepository;

	@Override
	public List<Cliente> allClientes() {
		// TODO Auto-generated method stub
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
	@PostMapping("/{id}")
	public Cliente obtenerCliente(Integer clienteId) {
		return clienteRepository.findById(clienteId).get();
	}
}
