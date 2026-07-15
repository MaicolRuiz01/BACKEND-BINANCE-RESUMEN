package com.binance.web.serviceImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.web.Entity.Efectivo;
import com.binance.web.Repository.EfectivoRepository;
import com.binance.web.service.EfectivoService;

@Service
public class EfectivoServiceImpl implements EfectivoService{
	
	@Autowired
    private EfectivoRepository efectivoRepo;

    @Override
    public Efectivo crearCaja(Efectivo caja) {
        caja.setId(null); // que sea nueva

        if (caja.getSaldo() == null) {
            caja.setSaldo(0.0);
        }

        // 👉 saldo inicial del día al momento de crear
        caja.setSaldoInicialDelDia(caja.getSaldo());

        return efectivoRepo.save(caja);
    }

    @Override
    public void eliminarCaja(Integer id) {
        Efectivo caja = efectivoRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Caja no encontrada: " + id));

        // Regla explícita: una caja solo se puede eliminar si ya no tiene NINGÚN
        // retirador vinculado (primero hay que eliminar o desvincular al retirador).
        if (caja.getRetirador() != null) {
            throw new IllegalStateException(
                    "No se puede eliminar la caja porque todavía está vinculada al retirador '"
                            + caja.getRetirador().getNombre()
                            + "'. Elimina primero ese retirador (la caja queda huérfana automáticamente) e inténtalo de nuevo.");
        }

        // Si la caja tiene movimientos/gastos asociados, la FK lanzará
        // DataIntegrityViolationException y el controller lo traduce a un 409.
        efectivoRepo.deleteById(id);
    }

}
