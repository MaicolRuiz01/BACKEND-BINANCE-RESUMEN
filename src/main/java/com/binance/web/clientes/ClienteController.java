package com.binance.web.clientes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.BuyDollars.BuyDollarsDto;
import com.binance.web.BuyDollars.BuyDollarsService;
import com.binance.web.Entity.Cliente;

@RestController
@RequestMapping("/cliente")
@CrossOrigin
public class ClienteController {
	
	@Autowired
	private ClienteService clienteService;
	@Autowired private BuyDollarsService buyDollarsService;

	// === Payload mínimo para transferir (sin crear archivos extra) ===
	public static class TransferReq {
		public Integer origenId;
		public Integer destinoId;
		public Double monto;
		public String nota;
	}

	@GetMapping("/listar")
	public List<Cliente> listar(){
		return clienteService.allClientes();
	}
	
	@PostMapping
	public ResponseEntity<Cliente> crearCliente(@RequestBody Cliente cliente) {
	    Cliente nuevo = clienteService.crearCliente(cliente);
	    return ResponseEntity.status(HttpStatus.CREATED).body(nuevo);
	}

	@PostMapping("/transferir")
	public ResponseEntity<?> transferir(@RequestBody TransferReq req) {
		clienteService.transferir(req.origenId, req.destinoId, req.monto, req.nota);
		// El front recarga la lista luego; basta con un OK y mensaje.
		Map<String, Object> resp = new HashMap<>();
		resp.put("mensaje", "Pago realizado con éxito");
		return ResponseEntity.ok(resp);
	}
	
	@PutMapping("/{id}")
    public ResponseEntity<Cliente> reemplazar(
            @PathVariable Integer id,
            @RequestBody Cliente nuevo) {
        Cliente actualizado = clienteService.reemplazar(id, nuevo);
        return ResponseEntity.ok(actualizado);
    }
	

    @GetMapping("/{clienteId}/compras")
    public List<BuyDollarsDto> listarComprasCliente(@PathVariable Integer clienteId) {
        return buyDollarsService.listarComprasPorCliente(clienteId);
    }
}
