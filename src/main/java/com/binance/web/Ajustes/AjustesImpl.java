package com.binance.web.Ajustes;

import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.Movimiento;

import java.util.List;
import com.binance.web.Entity.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.binance.web.Entity.Ajustes;

import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.MovimientoRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.Repository.AjustesRepository;



@Service
public class AjustesImpl implements AjustesService {


    @Autowired
	private ClienteRepository clienteRepository;
	
	@Autowired
	private MovimientoRepository movimientoRepository;

    @Autowired
    private SupplierRepository supplierRepository;
    
    @Autowired
    private AjustesRepository ajustesRepository;

    
    public List<Ajustes> allAjustes() {
        return ajustesRepository.findAll();
    }
    
public Ajustes crearAjuste( Ajustes ajuste) {

    // Movimiento
    if (ajuste.getMovimiento() != null && ajuste.getMovimiento().getId() != null) {
        Movimiento mov = movimientoRepository.findById(ajuste.getMovimiento().getId())
                .orElseThrow(() -> new RuntimeException("Movimiento no encontrado"));
        ajuste.setMovimiento(mov);

        mov.setMonto(ajuste.getMonto());
        movimientoRepository.save(mov);
    }

    // Cliente
    if (ajuste.getUsuarioCL() != null && ajuste.getUsuarioCL().getId() != null) {
        Cliente cliente = clienteRepository.findById(ajuste.getUsuarioCL().getId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        ajuste.setUsuarioCL(cliente);
    }

    // Proveedor 
    if (ajuste.getUsuarioPR() != null && ajuste.getUsuarioPR().getId() != null) {
        Supplier proveedor = supplierRepository.findById(ajuste.getUsuarioPR().getId())
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));
        ajuste.setUsuarioPR(proveedor);
    } else {
        ajuste.setUsuarioPR(null); // válido
    }
    
    return ajustesRepository.save(ajuste);
}

    public Ajustes obtenerAjuste(Integer ajusteId) {
        return ajustesRepository.findById(ajusteId)
                .orElseThrow(() -> new RuntimeException("El ajuste no existe"));
    }

    public List<Ajustes> obtenerajustesporUsuarioCL(int id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("El cliente no existe"));

        List<Ajustes> ajustesList = ajustesRepository.findAll()
        .stream()
        .filter(a -> a.getUsuarioCL() != null)
        .filter(a -> a.getUsuarioCL().getId().equals(id))
        .toList();

        return ajustesList;
    }

    public List<Ajustes> obtenerajustesporUsuarioPR(int id) {
     Supplier  supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("El proveedor no existe"));

       List<Ajustes> ajustesList = ajustesRepository.findAll()
        .stream()
        .filter(a -> a.getUsuarioPR() != null)
        .filter(a -> a.getUsuarioPR().getId().equals(id))
        .toList();

        return ajustesList;
    }



}

