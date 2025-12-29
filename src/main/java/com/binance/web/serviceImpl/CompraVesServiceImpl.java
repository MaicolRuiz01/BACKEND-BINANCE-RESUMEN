package com.binance.web.serviceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.Cliente;
import com.binance.web.Entity.CompraVES;
import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.ClienteRepository;
import com.binance.web.Repository.CompraVesRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.service.CompraVesService;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CompraVesServiceImpl implements CompraVesService {

    private final CompraVesRepository repo;
    private final SupplierRepository supplierRepository;
    private final ClienteRepository clienteRepository;

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    // =======================
    // CREATE
    // =======================
    @Override
    public CompraVES create(CompraVES compra) {
        validateAssignment(compra);
        normalize(compra);

        CompraVES saved = repo.save(compra);
        applyDebt(saved);

        return saved;
    }

    // =======================
    // UPDATE
    // =======================
    @Override
    public CompraVES update(Long id, CompraVES compra) {
        CompraVES existing = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Compra VES no encontrada: " + id));

        // 1️⃣ Revertir deuda anterior
        revertDebt(existing);

        // 2️⃣ Actualizar datos
        existing.setDate(compra.getDate() != null ? compra.getDate() : existing.getDate());
        existing.setBolivares(compra.getBolivares());
        existing.setTasa(compra.getTasa());
        existing.setCliente(compra.getCliente());
        existing.setSupplier(compra.getSupplier());

        // 3️⃣ Validar y recalcular
        validateAssignment(existing);
        normalize(existing);

        // 4️⃣ Guardar y aplicar nueva deuda
        CompraVES saved = repo.save(existing);
        applyDebt(saved);

        return saved;
    }

    // =======================
    // DELETE
    // =======================
    @Override
    public void delete(Long id) {
        CompraVES existing = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Compra VES no encontrada: " + id));

        revertDebt(existing);
        repo.delete(existing);
    }

    // =======================
    // READ
    // =======================
    @Override
    @Transactional(readOnly = true)
    public CompraVES findById(Long id) {
        return repo.findById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompraVES> findAll() {
        return repo.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompraVES> findByDay(LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        return repo.findByDateBetweenOrderByDateDesc(start, end);
    }

    // =======================
    // HELPERS
    // =======================

    /** Valida que la compra esté asignada a EXACTAMENTE uno */
    private void validateAssignment(CompraVES c) {
        boolean hasSupplier = c.getSupplier() != null;
        boolean hasCliente  = c.getCliente() != null;

        if (hasSupplier == hasCliente) {
            throw new RuntimeException(
                "La compra debe asignarse a un proveedor O a un cliente (solo uno)"
            );
        }
    }

    /** Normaliza fecha y calcula pesos */
    private void normalize(CompraVES c) {
        if (c.getDate() == null) {
            c.setDate(LocalDateTime.now(ZONE_BOGOTA));
        }

        double bol = c.getBolivares() != null ? c.getBolivares() : 0.0;
        double tasa = c.getTasa() != null ? c.getTasa() : 0.0;

        c.setPesos(bol * tasa);
    }

    /** Aplica deuda según asignación */
    private void applyDebt(CompraVES c) {
        double pesos = c.getPesos() != null ? c.getPesos() : 0.0;

        if (c.getSupplier() != null) {
            Supplier supplier = supplierRepository.findById(c.getSupplier().getId())
                    .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));

            double current = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
            supplier.setBalance(current + pesos);
            supplierRepository.save(supplier);
            return;
        }

        if (c.getCliente() != null) {
            Cliente cliente = clienteRepository.findById(c.getCliente().getId())
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

            double current = cliente.getSaldo() != null ? cliente.getSaldo() : 0.0;
            cliente.setSaldo(current + pesos);
            clienteRepository.save(cliente);
        }
    }

    /** Revierte la deuda anterior */
    private void revertDebt(CompraVES c) {
        double pesos = c.getPesos() != null ? c.getPesos() : 0.0;

        if (c.getSupplier() != null) {
            Supplier supplier = supplierRepository.findById(c.getSupplier().getId())
                    .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));

            double current = supplier.getBalance() != null ? supplier.getBalance() : 0.0;
            supplier.setBalance(current - pesos);
            supplierRepository.save(supplier);
        }

        if (c.getCliente() != null) {
            Cliente cliente = clienteRepository.findById(c.getCliente().getId())
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

            double current = cliente.getSaldo() != null ? cliente.getSaldo() : 0.0;
            cliente.setSaldo(current - pesos);
            clienteRepository.save(cliente);
        }
    }
}

